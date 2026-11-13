package com.sismics.docs.core.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.event.DocumentUpdatedAsyncEvent;
import com.sismics.docs.core.event.FileCreatedAsyncEvent;
import com.sismics.docs.core.event.FileDeletedAsyncEvent;
import com.sismics.docs.core.event.FileUpdatedAsyncEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.util.ImageDeskew;
import com.sismics.util.Scalr;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.io.InputStreamReaderThread;
import com.sismics.util.mime.MimeTypeUtil;

/**
 * File entity utilities.
 * 
 * @author bgamard
 */
public class FileUtil {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    /**
     * File ID of files currently being processed.
     */
    private static final Set<String> processingFileSet = Collections.synchronizedSet(new HashSet<>());
    
    /**
     * Optical character recognition on an image.
     *
     * @param language Language to OCR
     * @param image Buffered image
     * @return Content extracted
     * @throws Exception e
     */
    public static String ocrFile(String language, BufferedImage image) throws Exception {
        // Upscale, grayscale and deskew the image
        BufferedImage resizedImage = Scalr.resize(image, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, 3500, Scalr.OP_ANTIALIAS, Scalr.OP_GRAYSCALE);
        image.flush();
        ImageDeskew imageDeskew = new ImageDeskew(resizedImage);
        BufferedImage deskewedImage = Scalr.rotate(resizedImage, - imageDeskew.getSkewAngle(), Scalr.OP_ANTIALIAS, Scalr.OP_GRAYSCALE);
        resizedImage.flush();
        Path tmpFile = AppContext.getInstance().getFileService().createTemporaryFile();
        ImageIO.write(deskewedImage, "tiff", tmpFile.toFile());

        List<String> result = Lists.newLinkedList(Arrays.asList("tesseract", tmpFile.toAbsolutePath().toString(), "stdout", "-l", language));
        ProcessBuilder pb = new ProcessBuilder(result);
        Process process = pb.start();

        // Consume the process error stream
        final String commandName = pb.command().get(0);
        new InputStreamReaderThread(process.getErrorStream(), commandName).start();

        // Consume the data as text
        try (InputStream is = process.getInputStream()) {
            return CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
        }
    }

    /**
     * Remove a file from the storage filesystem.
     * 
     * @param file ID of file to delete
     */
    public static void delete(FileDeletedAsyncEvent event) throws IOException {
       File aFile = new File();
       aFile.setId(event.getFileId());
       aFile.setDocumentId(event.getDucumentId());
       aFile.setUserId(event.getUserId());
       aFile.setName(event.getFileName());
       aFile.setSize(event.getFileSize());
       delete(aFile);
    }
    /**
     * Remove a file from the storage filesystem.
     * 
     * @param file ID of file to delete
     */
    public static void delete(File file) throws IOException {
        Path storageDir = DirectoryUtil.getStorageDirectory(file);
        if (file.getName() != null) {
            String[] suffixes = new String[] { "", "_web", "_thumb", "_pdf" };
            for (String suffix : suffixes) {
                Path src = storageDir.resolve(file.getName() + suffix);
                if (Files.exists(src)) {
                    if (ConfigUtil.isFileDelete()) {
                        Files.deleteIfExists(src);
                    } else {
                        Path destDir = DirectoryUtil.getDeleteStorageDirectory(file);
                        Files.createDirectories(destDir);
                        Path dest = destDir.resolve(file.getName() + suffix);
                        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        try {
            Files.deleteIfExists(storageDir);
        } catch (IOException ignored) {
        }
    }

    /**
     * Create a new file.
     *
     * @param name File name, can be null
     * @param previousFileId ID of the previous version of the file, if the new file is a new version
     * @param unencryptedFile Path to the unencrypted file
     * @param fileSize File size
     * @param language File language, can be null if associated to no document
     * @param userId User ID creating the file
     * @param documentId Associated document ID or null if no document
     * @return File ID
     * @throws Exception e
     */
    public static String createFile(String name, String previousFileId, Path unencryptedFile, long fileSize, String language, String userId, String documentId) throws Exception {
        // Validate mime type
        String mimeType;
        try {
            mimeType = MimeTypeUtil.guessMimeType(unencryptedFile, name);
        } catch (IOException e) {
            throw new IOException("ErrorGuessMime", e);
        }

        // Validate user quota (quota <= 0 means unlimited)
        UserDao userDao = new UserDao();
        User user = userDao.getById(userId);
        if (user.getStorageQuota() > 0 && user.getStorageCurrent() + fileSize > user.getStorageQuota()) {
            throw new IOException("QuotaReached");
        }

        // Validate global quota
        String globalStorageQuotaStr = System.getenv(Constants.GLOBAL_QUOTA_ENV);
        if (!Strings.isNullOrEmpty(globalStorageQuotaStr)) {
            long globalStorageQuota = Long.parseLong(globalStorageQuotaStr);
            long globalStorageCurrent = userDao.getGlobalStorageCurrent();
            if (globalStorageCurrent + fileSize > globalStorageQuota) {
                throw new IOException("QuotaReached");
            }
        }

        // Prepare the file
        File file = new File();
        file.setOrder(0);
        file.setVersion(0);
        file.setLatestVersion(true);
        file.setDocumentId(documentId);
        file.setName(StringUtils.abbreviate(name, 200));
        file.setMimeType(mimeType);
        file.setUserId(userId);
        file.setSize(fileSize);

        // Get files of this document
        FileDao fileDao = new FileDao();
        if( !ConfigUtil.canFileDuplicate() && previousFileId==null ){
            if(fileDao.getByUserIdFileName(userId,StringUtils.abbreviate(name, 200))!=null){
                throw new IOException("FileNameDuplicate");
            }
        }
        if (documentId != null) {
            if (previousFileId == null) {
                // It's not a new version, so put it in last order
                file.setOrder(fileDao.getByDocumentId(userId, documentId).size());
            } else {
                // It's a new version, update the previous version
                File previousFile = fileDao.getActiveById(previousFileId);
                if (previousFile == null || !previousFile.getDocumentId().equals(documentId)) {
                    throw new IOException("Previous version mismatch");
                }

                if (previousFile.getVersionId() == null) {
                    previousFile.setVersionId(UUID.randomUUID().toString());
                }

                // Copy the previous file metadata
                file.setOrder(previousFile.getOrder());
                file.setVersionId(previousFile.getVersionId());
                file.setVersion(previousFile.getVersion() + 1);
                if (!Strings.isNullOrEmpty(previousFile.getName())) {
                    String prevBaseName = FilenameUtils.getBaseName(previousFile.getName());
                    if (prevBaseName.isEmpty()) {
                        prevBaseName = previousFile.getName();
                    }
                    String ext = name != null ? FilenameUtils.getExtension(name) : FilenameUtils.getExtension(previousFile.getName());
                    String inheritedName;
                    if (!ext.isEmpty()) {
                        int maxBaseLen = Math.max(1, 200 - 1 - ext.length());
                        String truncatedBase = prevBaseName.length() > maxBaseLen ? prevBaseName.substring(0, maxBaseLen) : prevBaseName;
                        inheritedName = StringUtils.abbreviate(truncatedBase + "." + ext, 200);
                    } else {
                        inheritedName = StringUtils.abbreviate(prevBaseName, 200);
                    }
                    file.setName(inheritedName);
                }

                // Update the previous file
                previousFile.setLatestVersion(false);
                fileDao.update(previousFile);
            }
        }

        // Create the file
        String fileId = fileDao.create(file, userId);

        // Ensure file encryption mode is permanently locked once any file is stored
        ConfigUtil.ensureFileEncryptLocked();

        // Save the file
        Path path = DirectoryUtil.getStorageDirectory(file).resolve(file.getName());
        try (InputStream inputStream = EncryptionUtil.encryptInputStream( Files.newInputStream(unencryptedFile),user.getPrivateKey())) {
            Files.copy(inputStream, path);
        }

        // Update the user quota
        user.setStorageCurrent(user.getStorageCurrent() + fileSize);
        userDao.updateQuota(user);

        // Raise a new file created event and document updated event if we have a document
        startProcessingFile(fileId);
        FileCreatedAsyncEvent fileCreatedAsyncEvent = new FileCreatedAsyncEvent();
        fileCreatedAsyncEvent.setUserId(userId);
        fileCreatedAsyncEvent.setLanguage(language);
        fileCreatedAsyncEvent.setFileId(file.getId());
        fileCreatedAsyncEvent.setUnencryptedFile(unencryptedFile);
        ThreadLocalContext.get().addAsyncEvent(fileCreatedAsyncEvent);

        if (documentId != null) {
            DocumentUpdatedAsyncEvent documentUpdatedAsyncEvent = new DocumentUpdatedAsyncEvent();
            documentUpdatedAsyncEvent.setUserId(userId);
            documentUpdatedAsyncEvent.setDocumentId(documentId);
            ThreadLocalContext.get().addAsyncEvent(documentUpdatedAsyncEvent);
        }

        return fileId;
    }

    /**
     * Silently swap/replace an existing file (latest or historical version) in-place
     * without creating a new version or writing an audit log entry.
     *
     * @param file Existing File entity to replace
     * @param name New file name
     * @param unencryptedFile Temporary path to the uploaded unencrypted file
     * @param fileSize Size of the new file in bytes
     * @param language Document language for OCR/extraction (can be null)
     * @return Updated File entity
     * @throws Exception e
     */
    public static File swapFile(File file, String name, Path unencryptedFile, long fileSize, String language) throws Exception {
        // Validate mime type
        String mimeType;
        try {
            mimeType = MimeTypeUtil.guessMimeType(unencryptedFile, name);
        } catch (IOException e) {
            throw new IOException("ErrorGuessMime", e);
        }

        // Validate and update owner quota
        UserDao userDao = new UserDao();
        User owner = userDao.getById(file.getUserId());
        long oldSize = (file.getSize() != null && file.getSize() > 0) ? file.getSize() : 0L;
        long sizeDelta = fileSize - oldSize;
        if (sizeDelta > 0) {
            if (owner.getStorageQuota() > 0 && owner.getStorageCurrent() + sizeDelta > owner.getStorageQuota()) {
                throw new IOException("QuotaReached");
            }
            String globalStorageQuotaStr = System.getenv(Constants.GLOBAL_QUOTA_ENV);
            if (!Strings.isNullOrEmpty(globalStorageQuotaStr)) {
                long globalStorageQuota = Long.parseLong(globalStorageQuotaStr);
                long globalStorageCurrent = userDao.getGlobalStorageCurrent();
                if (globalStorageCurrent + sizeDelta > globalStorageQuota) {
                    throw new IOException("QuotaReached");
                }
            }
        }

        String finalName = StringUtils.abbreviate(Strings.isNullOrEmpty(name) ? file.getName() : name, 200);
        Path storageDir = DirectoryUtil.getStorageDirectory(file);
        Files.createDirectories(storageDir);

        // Remove old physical file and its cached preview/thumbnail derivatives
        String oldName = file.getName();
        String[] suffixes = new String[] { "", "_web", "_thumb", "_pdf" };
        if (!Strings.isNullOrEmpty(oldName)) {
            for (String suffix : suffixes) {
                try {
                    Files.deleteIfExists(storageDir.resolve(oldName + suffix));
                } catch (IOException ignored) {
                }
            }
        }
        if (!Strings.isNullOrEmpty(finalName) && !finalName.equals(oldName)) {
            for (String suffix : suffixes) {
                try {
                    Files.deleteIfExists(storageDir.resolve(finalName + suffix));
                } catch (IOException ignored) {
                }
            }
        }

        // Update file metadata in DB (FileDao.update does NOT create any AuditLog)
        file.setName(finalName);
        file.setMimeType(mimeType);
        file.setSize(fileSize);
        file.setContent(null);
        FileDao fileDao = new FileDao();
        fileDao.update(file);

        // Ensure file encryption mode is permanently locked once any file is stored
        ConfigUtil.ensureFileEncryptLocked();

        // Save new physical file to storage directory
        Path newPath = storageDir.resolve(finalName);
        try (InputStream inputStream = EncryptionUtil.encryptInputStream(Files.newInputStream(unencryptedFile), owner.getPrivateKey())) {
            Files.copy(inputStream, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // Update owner storage quota
        long updatedCurrent = Math.max(0L, owner.getStorageCurrent() + sizeDelta);
        owner.setStorageCurrent(updatedCurrent);
        userDao.updateQuota(owner);

        // Trigger asynchronous thumbnail/preview generation and text extraction without audit logging
        startProcessingFile(file.getId());
        FileUpdatedAsyncEvent fileUpdatedAsyncEvent = new FileUpdatedAsyncEvent();
        fileUpdatedAsyncEvent.setUserId(file.getUserId());
        fileUpdatedAsyncEvent.setLanguage(language);
        fileUpdatedAsyncEvent.setFileId(file.getId());
        fileUpdatedAsyncEvent.setUnencryptedFile(unencryptedFile);
        ThreadLocalContext.get().addAsyncEvent(fileUpdatedAsyncEvent);

        return file;
    }

    /**
     * Start processing a file.
     *
     * @param fileId File ID
     */
    public static void startProcessingFile(String fileId) {
        processingFileSet.add(fileId);
        log.info("Processing started for file: " + fileId);
    }

    /**
     * End processing a file.
     *
     * @param fileId File ID
     */
    public static void endProcessingFile(String fileId) {
        processingFileSet.remove(fileId);
        log.info("Processing ended for file: " + fileId);
    }

    /**
     * Return true if a file is currently processing.
     *
     * @param fileId File ID
     * @return True if the file is processing
     */
    public static boolean isProcessingFile(String fileId) {
        return processingFileSet.contains(fileId);
    }

    /**
     * Get the size of a file on disk.
     *
     * @param file the file id
     * @param user   the file owner
     * @return the size or -1 if something went wrong
     */
    public static long getFileSize(File file, User user) {
        // To get the size we copy the decrypted content into a null output stream
        // and count the copied byte size.
        Path storedFile = DirectoryUtil.getStorageDirectory(file).resolve(file.getName());
        if (! Files.exists(storedFile)) {
            log.debug("File does not exist " + file.getId());
            return File.UNKNOWN_SIZE;
        }
        try (InputStream fileInputStream = Files.newInputStream(storedFile);
             InputStream inputStream = EncryptionUtil.decryptInputStream(fileInputStream, user.getPrivateKey());
             CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        ) {
            IOUtils.copy(countingInputStream, NullOutputStream.NULL_OUTPUT_STREAM);
            return countingInputStream.getByteCount();
        } catch (Exception e) {
            log.debug("Can't find size of file " + file.getId(), e);
            return File.UNKNOWN_SIZE;
        }
    }

    /**
     * Transfers physical file storage (moving directories in unencrypted mode or re-encrypting in encrypted mode)
     * and updates the file's userId from sourceUser to targetUser.
     *
     * @param file File entity to transfer
     * @param sourceUser Previous owner
     * @param targetUser New owner
     */
    public static void transferFileOwnership(File file, User sourceUser, User targetUser) {
        String[] suffixes = new String[] { "", "_web", "_thumb", "_pdf" };
        if (!ConfigUtil.isFileEncrypt()) {
            Path oldDir = DirectoryUtil.getStorageDirectory(file);
            file.setUserId(targetUser.getId());
            Path newDir = DirectoryUtil.getStorageDirectory(file);
            if (!oldDir.equals(newDir) && Files.isDirectory(oldDir)) {
                try {
                    Files.createDirectories(newDir);
                    for (String suffix : suffixes) {
                        Path src = oldDir.resolve(file.getName() + suffix);
                        Path dst = newDir.resolve(file.getName() + suffix);
                        if (Files.exists(src)) {
                            Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    // Move any remaining files in oldDir if present
                    try (java.util.stream.Stream<Path> stream = Files.list(oldDir)) {
                        for (Path entry : stream.collect( java.util.stream.Collectors.toList())) {
                            if (Files.isRegularFile(entry)) {
                                Files.move(entry, newDir.resolve(entry.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    deleteEmptyDirectoryUpwards(oldDir, DirectoryUtil.getBaseDataDirectory().resolve("storage"));
                } catch (Exception e) {
                    log.error("Error moving storage directory for file " + file.getId() + " from " + oldDir + " to " + newDir, e);
                }
            }
        } else {
            Path storageDir = DirectoryUtil.getStorageDirectory(file);
            for (String suffix : suffixes) {
                Path targetPath = storageDir.resolve(file.getName() + suffix);
                if (Files.exists(targetPath)) {
                    Path tmpDecrypted = null;
                    try {
                        tmpDecrypted = EncryptionUtil.decryptFile(targetPath, sourceUser.getPrivateKey());
                        try (InputStream encryptedIn = EncryptionUtil.encryptInputStream(Files.newInputStream(tmpDecrypted), targetUser.getPrivateKey())) {
                            Files.copy(encryptedIn, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        log.error("Error re-encrypting file " + file.getId() + suffix + " during ownership transfer", e);
                    } finally {
                        if (tmpDecrypted != null && !tmpDecrypted.equals(targetPath)) {
                            try {
                                Files.deleteIfExists(tmpDecrypted);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            }
            file.setUserId(targetUser.getId());
        }
    }

    private static void deleteEmptyDirectoryUpwards(Path dir, Path stopAtExclusive) {
        Path current = dir;
        while (current != null && !current.equals(stopAtExclusive) && current.startsWith(stopAtExclusive)) {
            try {
                if (Files.isDirectory(current)) {
                    try (java.util.stream.Stream<Path> entries = Files.list(current)) {
                        if (!entries.findFirst().isPresent()) {
                            Files.deleteIfExists(current);
                            current = current.getParent();
                            continue;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            break;
        }
    }
}
