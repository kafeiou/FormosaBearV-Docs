package com.sismics.docs.core.listener.async;

import com.google.common.base.Strings;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.event.RebuildIndexAsyncEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.util.EncryptionUtil;
import com.sismics.docs.core.util.TransactionUtil;
import com.sismics.docs.core.util.format.FormatHandler;
import com.sismics.docs.core.util.format.FormatHandlerUtil;
import com.sismics.docs.core.util.indexing.ReindexProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listener on rebuild index.
 *
 * @author bgamard
 */
public class RebuildIndexAsyncListener {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(RebuildIndexAsyncListener.class);

    private static final class OcrCandidate {
        final String fileId;
        final String fileName;
        final String mimeType;
        final String userId;
        final String documentId;
        final boolean hasContent;

        OcrCandidate(File f) {
            this.fileId = f.getId();
            this.fileName = f.getName();
            this.mimeType = f.getMimeType();
            this.userId = f.getUserId();
            this.documentId = f.getDocumentId();
            this.hasContent = f.getContent() != null && !f.getContent().trim().isEmpty();
        }
    }

    /**
     * Rebuild Lucene index (and optionally re-run OCR text extraction).
     *
     * @param event Index rebuild event
     */
    @Subscribe
    @AllowConcurrentEvents
    public void on(final RebuildIndexAsyncEvent event) {
        RebuildIndexAsyncEvent.OcrMode ocrMode = event != null ? event.getOcrMode() : RebuildIndexAsyncEvent.OcrMode.NONE;
        boolean startupMaintenance = event != null && event.isStartupMaintenance();
        if (log.isInfoEnabled()) {
            log.info("Rebuild index event: " + event);
        }

        ReindexProgress.beginExecution(ocrMode, startupMaintenance);
        try {
            // Read initial totals
            TransactionUtil.handle(() -> {
                long docCount = new DocumentDao().getDocumentCount();
                long fileCount = new FileDao().getFileCount();
                ReindexProgress.setTotals(docCount, fileCount);
                if (ocrMode != RebuildIndexAsyncEvent.OcrMode.NONE) {
                    ConfigUtil.getOcrConfig();
                }
            });

            // Optional OCR / text extraction pass before indexing files
            if (ocrMode != RebuildIndexAsyncEvent.OcrMode.NONE) {
                reprocessOcr(ocrMode);
            }

            // Clear the Lucene index
            ReindexProgress.setPhase(ReindexProgress.Phase.CLEARING);
            AppContext.getInstance().getIndexingHandler().clearIndex();

            // Index all documents
            ReindexProgress.setPhase(ReindexProgress.Phase.INDEXING_DOCUMENTS);
            TransactionUtil.handle(() -> {
                int offset = 0;
                DocumentDao documentDao = new DocumentDao();
                List<Document> documentList;
                do {
                    documentList = documentDao.findAll(offset, 100);
                    if (!documentList.isEmpty()) {
                        AppContext.getInstance().getIndexingHandler().createDocuments(documentList);
                        String lastTitle = documentList.get(documentList.size() - 1).getTitle();
                        ReindexProgress.addProcessedDocuments(documentList.size(), lastTitle);
                    }
                    offset += 100;
                } while (documentList.size() > 0);
            });

            // Index all files
            ReindexProgress.setPhase(ReindexProgress.Phase.INDEXING_FILES);
            TransactionUtil.handle(() -> {
                int offset = 0;
                FileDao fileDao = new FileDao();
                List<File> fileList;
                do {
                    fileList = fileDao.findAll(offset, 100);
                    if (!fileList.isEmpty()) {
                        AppContext.getInstance().getIndexingHandler().createFiles(fileList);
                        String lastName = fileList.get(fileList.size() - 1).getName();
                        ReindexProgress.addProcessedFiles(fileList.size(), lastName);
                    }
                    offset += 100;
                } while (fileList.size() > 0);
            });

            ReindexProgress.complete();
            if (log.isInfoEnabled()) {
                log.info("Rebuilding index done");
            }
        } catch (Throwable t) {
            log.error("Error rebuilding index", t);
            ReindexProgress.fail(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    private void reprocessOcr(RebuildIndexAsyncEvent.OcrMode ocrMode) {
        ReindexProgress.setPhase(ReindexProgress.Phase.OCR_REPROCESSING);
        List<OcrCandidate> candidates = new ArrayList<>();
        AtomicReference<String> defaultLanguageRef = new AtomicReference<>("eng");

        TransactionUtil.handle(() -> {
            String defLang = ConfigUtil.getConfigStringValue(ConfigType.DEFAULT_LANGUAGE);
            if (!Strings.isNullOrEmpty(defLang)) {
                defaultLanguageRef.set(defLang);
            }
            int offset = 0;
            FileDao fileDao = new FileDao();
            List<File> batch;
            do {
                batch = fileDao.findAll(offset, 100);
                for (File f : batch) {
                    candidates.add(new OcrCandidate(f));
                }
                offset += 100;
            } while (!batch.isEmpty());
        });

        ReindexProgress.setOcrTotal(candidates.size());
        String defaultLanguage = defaultLanguageRef.get();

        for (OcrCandidate candidate : candidates) {
            String displayName = Strings.isNullOrEmpty(candidate.fileName) ? candidate.fileId : candidate.fileName;
            ReindexProgress.setCurrentItem(displayName);

            if (ocrMode == RebuildIndexAsyncEvent.OcrMode.MISSING && candidate.hasContent) {
                ReindexProgress.incrementOcrSkipped();
                continue;
            }

            FormatHandler formatHandler = FormatHandlerUtil.find(candidate.mimeType);
            if (formatHandler == null) {
                ReindexProgress.incrementOcrSkipped();
                continue;
            }

            AtomicReference<File> fileRef = new AtomicReference<>();
            AtomicReference<User> userRef = new AtomicReference<>();
            AtomicReference<String> languageRef = new AtomicReference<>(defaultLanguage);

            TransactionUtil.handle(() -> {
                File f = new FileDao().getActiveById(candidate.fileId);
                if (f == null) {
                    return;
                }
                fileRef.set(f);
                userRef.set(new UserDao().getById(f.getUserId()));
                if (!Strings.isNullOrEmpty(f.getDocumentId())) {
                    Document doc = new DocumentDao().getById(f.getDocumentId());
                    if (doc != null && !Strings.isNullOrEmpty(doc.getLanguage())) {
                        languageRef.set(doc.getLanguage());
                    }
                }
                ConfigUtil.getOcrConfig();
            });

            File file = fileRef.get();
            User user = userRef.get();
            if (file == null || user == null) {
                ReindexProgress.incrementOcrSkipped();
                continue;
            }

            Path unencryptedFile = null;
            try {
                Path storedFile = DirectoryUtil.getStorageDirectory(file).resolve(file.getName());
                if (!Files.exists(storedFile)) {
                    ReindexProgress.addOcrFailure(candidate.fileId, displayName, "Stored file not found on disk");
                    continue;
                }
                unencryptedFile = EncryptionUtil.decryptFile(storedFile, user.getPrivateKey());
                String content = formatHandler.extractContent(languageRef.get(), unencryptedFile);

                final String extractedContent = content;
                TransactionUtil.handle(() -> {
                    FileDao fileDao = new FileDao();
                    File freshFile = fileDao.getActiveById(candidate.fileId);
                    if (freshFile != null) {
                        freshFile.setContent(extractedContent);
                        fileDao.update(freshFile);
                    }
                });
                ReindexProgress.incrementOcrSucceeded();
            } catch (Throwable e) {
                log.error("Error reprocessing OCR for file: " + displayName, e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ReindexProgress.addOcrFailure(candidate.fileId, displayName, msg);
            } finally {
                if (unencryptedFile != null) {
                    try {
                        Files.deleteIfExists(unencryptedFile);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
