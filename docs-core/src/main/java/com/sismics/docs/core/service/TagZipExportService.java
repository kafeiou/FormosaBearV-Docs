package com.sismics.docs.core.service;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.criteria.TagCriteria;
import com.sismics.docs.core.dao.dto.TagDto;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Prepares and streams a hierarchical ZIP archive of downloadable files under a tag subtree,
 * organized as {@code Tag/SubTag/DocumentTitle/FileName}, together with a UTF-8 BOM
 * {@code _manifest.txt} listing every readable document and file (including read-only files
 * that were skipped because download is not permitted).
 */
public class TagZipExportService {
    private static final Logger log = LoggerFactory.getLogger(TagZipExportService.class);
    private static final int MAX_SEGMENT_LEN = 80;

    public enum FileStatus {
        INCLUDED,
        SKIPPED_DOWNLOAD_DISABLED,
        SKIPPED_MISSING_ON_DISK
    }

    public static final class PlannedFile {
        public final String fileId;
        public final String fileName;
        public final long size;
        public final FileStatus status;
        public final String zipEntryPath;
        public final Path storedPath;
        public final String ownerPrivateKey;
        public final boolean encrypted;

        public PlannedFile(String fileId, String fileName, long size, FileStatus status,
                           String zipEntryPath, Path storedPath, String ownerPrivateKey, boolean encrypted) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.size = size;
            this.status = status;
            this.zipEntryPath = zipEntryPath;
            this.storedPath = storedPath;
            this.ownerPrivateKey = ownerPrivateKey;
            this.encrypted = encrypted;
        }
    }

    public static final class PlannedDocument {
        public final String documentId;
        public final String title;
        public final boolean downloadAllowed;
        public final List<PlannedFile> files = new ArrayList<>();

        public PlannedDocument(String documentId, String title, boolean downloadAllowed) {
            this.documentId = documentId;
            this.title = title;
            this.downloadAllowed = downloadAllowed;
        }
    }

    public static final class PlannedTag {
        public final String tagId;
        public final String name;
        public final String displayPath;
        public final String zipFolderPath;
        public final int depth;
        public final List<PlannedDocument> documents = new ArrayList<>();

        public PlannedTag(String tagId, String name, String displayPath, String zipFolderPath, int depth) {
            this.tagId = tagId;
            this.name = name;
            this.displayPath = displayPath;
            this.zipFolderPath = zipFolderPath;
            this.depth = depth;
        }
    }

    public static final class ExportPlan {
        public final String rootTagId;
        public final String rootTagName;
        public final String exportedBy;
        public final long exportedAt;
        public final boolean globalDownloadEnabled;
        public final List<PlannedTag> tags = new ArrayList<>();
        public int totalDocuments;
        public int totalFiles;
        public int includedFiles;
        public int skippedDownloadDisabled;
        public int skippedMissingOnDisk;
        public long includedBytes;

        public ExportPlan(String rootTagId, String rootTagName, String exportedBy,
                          long exportedAt, boolean globalDownloadEnabled) {
            this.rootTagId = rootTagId;
            this.rootTagName = rootTagName;
            this.exportedBy = exportedBy;
            this.exportedAt = exportedAt;
            this.globalDownloadEnabled = globalDownloadEnabled;
        }
    }

    /**
     * Builds the complete export plan inside the active database transaction so that
     * streaming the ZIP afterwards requires zero database access.
     */
    public ExportPlan preparePlan(String rootTagId, List<String> targetIdList,
                                  String principalId, String principalName, boolean isAdmin) {
        return preparePlan(rootTagId, targetIdList, principalId, principalName, isAdmin, false);
    }

    public ExportPlan preparePlan(String rootTagId, List<String> targetIdList,
                                  String principalId, String principalName, boolean isAdmin,
                                  boolean includeVersions) {
        TagDao tagDao = new TagDao();
        List<TagDto> readableTags = tagDao.findByCriteria(
                new TagCriteria().setTargetIdList(targetIdList), null);

        Map<String, TagDto> tagMap = new HashMap<>();
        Map<String, List<TagDto>> childrenMap = new HashMap<>();
        for (TagDto t : readableTags) {
            if (t.isHidden() || t.isEffectiveHidden()) {
                continue;
            }
            tagMap.put(t.getId(), t);
        }

        TagDto rootTag = tagMap.get(rootTagId);
        if (rootTag == null || rootTag.isContainer()) {
            return null;
        }

        for (TagDto t : tagMap.values()) {
            if (t.getParentId() != null && tagMap.containsKey(t.getParentId())) {
                childrenMap.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t);
            }
        }
        Comparator<TagDto> tagComparator = (a, b) -> {
            int ordA = a.getOrder() != null ? a.getOrder() : Integer.MAX_VALUE;
            int ordB = b.getOrder() != null ? b.getOrder() : Integer.MAX_VALUE;
            if (ordA != ordB) {
                return Integer.compare(ordA, ordB);
            }
            long dateA = a.getCreateDate() != null ? a.getCreateDate().getTime() : 0L;
            long dateB = b.getCreateDate() != null ? b.getCreateDate().getTime() : 0L;
            if (dateA != dateB) {
                return Long.compare(dateA, dateB);
            }
            return Strings.nullToEmpty(a.getName()).compareToIgnoreCase(Strings.nullToEmpty(b.getName()));
        };
        for (List<TagDto> kids : childrenMap.values()) {
            kids.sort(tagComparator);
        }

        boolean globalDownload = ConfigUtil.canFileDownload();
        boolean fileEncrypt = ConfigUtil.isFileEncrypt();
        Path baseDataDir = DirectoryUtil.getBaseDataDirectory().toAbsolutePath().normalize();

        ExportPlan plan = new ExportPlan(
                rootTag.getId(),
                rootTag.getName(),
                Strings.nullToEmpty(principalName),
                System.currentTimeMillis(),
                globalDownload
        );

        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();
        UserDao userDao = new UserDao();
        Map<String, Document> docCache = new HashMap<>();
        Map<String, String> privateKeyCache = new HashMap<>();
        Set<String> usedZipEntries = new HashSet<>();
        Set<String> visitedTags = new HashSet<>();

        traverseTag(rootTag, "", "", 0, childrenMap, visitedTags,
                tagDao, documentDao, fileDao, userDao, targetIdList,
                principalId, isAdmin, globalDownload, fileEncrypt, includeVersions, baseDataDir,
                docCache, privateKeyCache, usedZipEntries, plan);

        return plan;
    }

    private void traverseTag(TagDto tag, String parentDisplayPath, String parentZipPath, int depth,
                             Map<String, List<TagDto>> childrenMap, Set<String> visitedTags,
                             TagDao tagDao, DocumentDao documentDao, FileDao fileDao, UserDao userDao,
                             List<String> targetIdList, String principalId, boolean isAdmin,
                             boolean globalDownload, boolean fileEncrypt, boolean includeVersions, Path baseDataDir,
                             Map<String, Document> docCache, Map<String, String> privateKeyCache,
                             Set<String> usedZipEntries, ExportPlan plan) {
        if (!visitedTags.add(tag.getId())) {
            return;
        }

        String safeTagSegment = sanitizePathSegment(tag.getName(), "tag_" + tag.getId().substring(0, Math.min(8, tag.getId().length())));
        String displayPath = parentDisplayPath.isEmpty() ? tag.getName() : parentDisplayPath + " / " + tag.getName();
        String zipFolderPath = parentZipPath.isEmpty() ? safeTagSegment : parentZipPath + "/" + safeTagSegment;

        PlannedTag plannedTag = new PlannedTag(tag.getId(), tag.getName(), displayPath, zipFolderPath, depth);

        if (!tag.isContainer()) {
            List<TagDao.TreeDocumentItem> directDocs = loadAllDirectDocuments(tagDao, tag.getId(), targetIdList);
            if (!directDocs.isEmpty()) {
                List<String> docIds = new ArrayList<>(directDocs.size());
                for (TagDao.TreeDocumentItem item : directDocs) {
                    docIds.add(item.getId());
                }
                List<File> allFiles = includeVersions
                        ? fileDao.getAllVersionsByDocumentsIds(docIds)
                        : fileDao.getByDocumentsIds(docIds);
                Map<String, List<File>> filesByDoc = new HashMap<>();
                for (File f : allFiles) {
                    if (f.getDocumentId() != null) {
                        filesByDoc.computeIfAbsent(f.getDocumentId(), k -> new ArrayList<>()).add(f);
                    }
                }

                Set<String> usedDocFoldersInTag = new HashSet<>();
                for (TagDao.TreeDocumentItem item : directDocs) {
                    Document doc = docCache.computeIfAbsent(item.getId(), documentDao::getById);
                    if (doc == null || doc.getDeleteDate() != null) {
                        continue;
                    }
                    boolean downloadAllowed = globalDownload
                            || isAdmin
                            || (principalId != null && principalId.equals(doc.getUserId()))
                            || doc.isAllowDownload();

                    String docTitle = Strings.isNullOrEmpty(doc.getTitle()) ? doc.getId() : doc.getTitle();
                    String baseDocFolder = sanitizePathSegment(docTitle, "doc_" + doc.getId().substring(0, Math.min(8, doc.getId().length())));
                    String docFolder = deduplicateSegment(baseDocFolder, usedDocFoldersInTag);

                    PlannedDocument plannedDoc = new PlannedDocument(doc.getId(), docTitle, downloadAllowed);
                    plan.totalDocuments++;

                    List<File> docFiles = filesByDoc.getOrDefault(doc.getId(), Collections.emptyList());
                    List<File> orderedDocFiles = includeVersions ? orderAndFilterVersionGroups(docFiles) : docFiles;
                    Map<String, Integer> groupSizes = new HashMap<>();
                    if (includeVersions) {
                        for (File f : orderedDocFiles) {
                            String gk = Strings.isNullOrEmpty(f.getVersionId()) ? f.getId() : f.getVersionId();
                            groupSizes.merge(gk, 1, Integer::sum);
                        }
                    }

                    for (File file : orderedDocFiles) {
                        plan.totalFiles++;
                        String rawName = Strings.isNullOrEmpty(file.getName()) ? file.getId() : file.getName();
                        String safeFileName = sanitizeFileName(rawName, file.getId());
                        if (includeVersions) {
                            String gk = Strings.isNullOrEmpty(file.getVersionId()) ? file.getId() : file.getVersionId();
                            if (groupSizes.getOrDefault(gk, 1) > 1) {
                                safeFileName = appendVersionSuffix(safeFileName, file);
                            }
                        }
                        long fileSize = (file.getSize() != null && file.getSize() > 0) ? file.getSize() : 0L;

                        String displayEntryPath = zipFolderPath + "/" + docFolder + "/" + safeFileName;
                        if (!downloadAllowed) {
                            plannedDoc.files.add(new PlannedFile(
                                    file.getId(), rawName, fileSize,
                                    FileStatus.SKIPPED_DOWNLOAD_DISABLED,
                                    displayEntryPath, null, null, fileEncrypt));
                            plan.skippedDownloadDisabled++;
                            continue;
                        }

                        Path storageDir = resolveStorageDirReadOnly(file, fileEncrypt, baseDataDir);
                        Path storedPath = storageDir.resolve(rawName).toAbsolutePath().normalize();
                        if (!Files.isRegularFile(storedPath)) {
                            plannedDoc.files.add(new PlannedFile(
                                    file.getId(), rawName, fileSize,
                                    FileStatus.SKIPPED_MISSING_ON_DISK,
                                    displayEntryPath, null, null, fileEncrypt));
                            plan.skippedMissingOnDisk++;
                            continue;
                        }

                        String entryPath = deduplicateZipEntryPath(displayEntryPath, usedZipEntries);
                        String ownerPrivateKey = null;
                        if (fileEncrypt && !Strings.isNullOrEmpty(file.getUserId())) {
                            ownerPrivateKey = privateKeyCache.computeIfAbsent(file.getUserId(), uid -> {
                                User u = userDao.getById(uid);
                                return u != null ? u.getPrivateKey() : null;
                            });
                        }

                        plannedDoc.files.add(new PlannedFile(
                                file.getId(), rawName, fileSize,
                                FileStatus.INCLUDED,
                                entryPath, storedPath, ownerPrivateKey, fileEncrypt));
                        plan.includedFiles++;
                        plan.includedBytes += fileSize;
                    }

                    plannedTag.documents.add(plannedDoc);
                }
            }
        }

        plan.tags.add(plannedTag);

        List<TagDto> children = childrenMap.getOrDefault(tag.getId(), Collections.emptyList());
        for (TagDto child : children) {
            traverseTag(child, displayPath, zipFolderPath, depth + 1, childrenMap, visitedTags,
                    tagDao, documentDao, fileDao, userDao, targetIdList,
                    principalId, isAdmin, globalDownload, fileEncrypt, includeVersions, baseDataDir,
                    docCache, privateKeyCache, usedZipEntries, plan);
        }
    }

    /**
     * Groups all active versions of a document by versionId, discards orphaned historical groups
     * without an active latest version, and orders groups by their latest version's file order
     * with versions sorted ascending inside each group.
     */
    private List<File> orderAndFilterVersionGroups(List<File> allDocFiles) {
        Map<String, List<File>> groups = new LinkedHashMap<>();
        Map<String, File> latestByGroup = new HashMap<>();
        for (File f : allDocFiles) {
            String gk = Strings.isNullOrEmpty(f.getVersionId()) ? f.getId() : f.getVersionId();
            groups.computeIfAbsent(gk, k -> new ArrayList<>()).add(f);
            if (f.isLatestVersion()) {
                latestByGroup.put(gk, f);
            }
        }
        List<String> validGroupKeys = new ArrayList<>();
        for (String gk : groups.keySet()) {
            if (latestByGroup.containsKey(gk)) {
                validGroupKeys.add(gk);
            }
        }
        validGroupKeys.sort(Comparator.comparingInt(gk -> {
            File latest = latestByGroup.get(gk);
            return (latest != null && latest.getOrder() != null) ? latest.getOrder() : Integer.MAX_VALUE;
        }));
        List<File> result = new ArrayList<>();
        for (String gk : validGroupKeys) {
            List<File> grp = groups.get(gk);
            grp.sort(Comparator.comparingInt((File f) -> f.getVersion() != null ? f.getVersion() : 0)
                    .thenComparingLong(f -> f.getCreateDate() != null ? f.getCreateDate().getTime() : 0L));
            result.addAll(grp);
        }
        return result;
    }

    private static String appendVersionSuffix(String safeFileName, File file) {
        int seq = (file.getVersion() != null ? file.getVersion() : 0) + 1;
        String suffix = "_#" + seq;
        if (!Strings.isNullOrEmpty(file.getVersionLabel()) && !file.getVersionLabel().trim().isEmpty()) {
            String safeLabel = sanitizePathSegment(file.getVersionLabel().trim(), "");
            if (!safeLabel.isEmpty()) {
                suffix += "_" + safeLabel;
            }
        }
        int dot = safeFileName.lastIndexOf('.');
        if (dot > 0) {
            return safeFileName.substring(0, dot) + suffix + safeFileName.substring(dot);
        }
        return safeFileName + suffix;
    }

    /**
     * Streams the prepared export plan into the given output stream as a UTF-8 ZIP archive
     * without performing any database queries.
     */
    public void writeZip(ExportPlan plan, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            // 1. Write _manifest.txt first (UTF-8 with BOM for clean Windows Notepad rendering)
            String manifest = buildManifestText(plan);
            ZipEntry manifestEntry = new ZipEntry("_manifest.txt");
            zos.putNextEntry(manifestEntry);
            zos.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. Stream each included file directly from disk
            for (PlannedTag tag : plan.tags) {
                for (PlannedDocument doc : tag.documents) {
                    for (PlannedFile file : doc.files) {
                        if (file.status != FileStatus.INCLUDED || file.storedPath == null || file.zipEntryPath == null) {
                            continue;
                        }
                        try (InputStream rawIn = Files.newInputStream(file.storedPath);
                             InputStream in = file.encrypted
                                     ? EncryptionUtil.decryptInputStream(rawIn, file.ownerPrivateKey)
                                     : rawIn) {
                            ZipEntry entry = new ZipEntry(file.zipEntryPath);
                            zos.putNextEntry(entry);
                            ByteStreams.copy(in, zos);
                            zos.closeEntry();
                        } catch (Exception e) {
                            log.warn("Error streaming file into tag ZIP: " + file.fileId + " (" + file.fileName + ")", e);
                        }
                    }
                }
            }
            zos.finish();
        }
    }

    private String buildManifestText(ExportPlan plan) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\r\n");
        sb.append("FormosaBearV Tag Hierarchy ZIP Export Manifest\r\n");
        sb.append("================================================================================\r\n");
        sb.append("Root Tag       : ").append(plan.rootTagName).append("\r\n");
        sb.append("Exported By    : ").append(plan.exportedBy).append("\r\n");
        sb.append("Exported At    : ").append(sdf.format(new Date(plan.exportedAt))).append("\r\n");
        sb.append("Global Download: ").append(plan.globalDownloadEnabled ? "Enabled" : "Restricted (per-document allow_download)").append("\r\n");
        sb.append("--------------------------------------------------------------------------------\r\n");
        sb.append("Summary:\r\n");
        sb.append("  Tags Scanned              : ").append(plan.tags.size()).append("\r\n");
        sb.append("  Documents Found           : ").append(plan.totalDocuments).append("\r\n");
        sb.append("  Total Files               : ").append(plan.totalFiles).append("\r\n");
        sb.append("  Included in ZIP           : ").append(plan.includedFiles)
                .append(" (").append(formatBytes(plan.includedBytes)).append(")\r\n");
        sb.append("  Skipped (Download Denied) : ").append(plan.skippedDownloadDisabled).append("\r\n");
        sb.append("  Skipped (Missing on Disk) : ").append(plan.skippedMissingOnDisk).append("\r\n");
        sb.append("================================================================================\r\n\r\n");

        for (PlannedTag tag : plan.tags) {
            for (PlannedDocument doc : tag.documents) {
                for (PlannedFile f : doc.files) {
                    String path = f.zipEntryPath != null ? f.zipEntryPath : f.fileName;
                    String statusLabel = (f.status == FileStatus.INCLUDED) ? "[O]" : "[X]";
                    sb.append(statusLabel).append(' ').append(path).append("\r\n");
                }
            }
        }
        return sb.toString();
    }

    private List<TagDao.TreeDocumentItem> loadAllDirectDocuments(TagDao tagDao, String tagId, List<String> targetIdList) {
        List<TagDao.TreeDocumentItem> all = new ArrayList<>();
        int offset = 0;
        int pageSize = 100;
        while (true) {
            TagDao.TreeDocumentPage page = tagDao.findDirectDocumentsByTag(tagId, targetIdList, offset, pageSize);
            if (page.getDocuments().isEmpty()) {
                break;
            }
            all.addAll(page.getDocuments());
            offset += page.getDocuments().size();
            if (offset >= page.getTotal() || page.getDocuments().size() < pageSize) {
                break;
            }
        }
        return all;
    }

    private Path resolveStorageDirReadOnly(File file, boolean fileEncrypt, Path baseDataDir) {
        if (!fileEncrypt && file != null) {
            if (Strings.isNullOrEmpty(file.getDocumentId()) || file.getDocumentId().trim().isEmpty()) {
                return baseDataDir.resolve("storage/" + file.getUserId() + "/" + file.getId()).toAbsolutePath().normalize();
            }
            Path twoLevelDir = baseDataDir.resolve("storage/" + file.getUserId() + "/" + file.getId()).toAbsolutePath().normalize();
            Path threeLevelDir = baseDataDir.resolve("storage/" + file.getUserId() + "/" + file.getDocumentId() + "/" + file.getId()).toAbsolutePath().normalize();
            if (Files.isDirectory(twoLevelDir) && !Files.isDirectory(threeLevelDir)) {
                return twoLevelDir;
            }
            return threeLevelDir;
        }
        if (file != null && !Strings.isNullOrEmpty(file.getId())) {
            Path perFileDir = baseDataDir.resolve("storage/" + file.getId()).toAbsolutePath().normalize();
            if (Files.isDirectory(perFileDir)) {
                return perFileDir;
            }
            if (!Strings.isNullOrEmpty(file.getName()) && Files.exists(baseDataDir.resolve("storage").resolve(file.getName()))) {
                return baseDataDir.resolve("storage").toAbsolutePath().normalize();
            }
            return perFileDir;
        }
        return baseDataDir.resolve("storage").toAbsolutePath().normalize();
    }

    public static String sanitizePathSegment(String raw, String fallback) {
        if (Strings.isNullOrEmpty(raw)) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_")
                .replaceAll("^\\.+|\\.+$", "")
                .trim();
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return fallback;
        }
        if (cleaned.length() > MAX_SEGMENT_LEN) {
            cleaned = cleaned.substring(0, MAX_SEGMENT_LEN).trim();
        }
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String sanitizeFileName(String raw, String fallback) {
        if (Strings.isNullOrEmpty(raw)) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_").trim();
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return fallback;
        }
        if (cleaned.length() > 120) {
            int dot = cleaned.lastIndexOf('.');
            if (dot > 0 && dot > cleaned.length() - 16) {
                String ext = cleaned.substring(dot);
                cleaned = cleaned.substring(0, Math.max(1, 120 - ext.length())) + ext;
            } else {
                cleaned = cleaned.substring(0, 120);
            }
        }
        return cleaned;
    }

    private static String deduplicateSegment(String base, Set<String> usedLower) {
        String candidate = base;
        int n = 2;
        while (!usedLower.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + " (" + n + ")";
            n++;
        }
        return candidate;
    }

    private static String deduplicateZipEntryPath(String fullPath, Set<String> usedLower) {
        if (usedLower.add(fullPath.toLowerCase(Locale.ROOT))) {
            return fullPath;
        }
        int slash = fullPath.lastIndexOf('/');
        String dir = slash >= 0 ? fullPath.substring(0, slash + 1) : "";
        String file = slash >= 0 ? fullPath.substring(slash + 1) : fullPath;
        int dot = file.lastIndexOf('.');
        String stem = dot > 0 ? file.substring(0, dot) : file;
        String ext = dot > 0 ? file.substring(dot) : "";
        int n = 2;
        while (true) {
            String candidate = dir + stem + " (" + n + ")" + ext;
            if (usedLower.add(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
            n++;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
