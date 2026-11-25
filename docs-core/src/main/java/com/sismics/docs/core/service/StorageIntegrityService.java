package com.sismics.docs.core.service;

import com.google.common.base.Strings;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.DirectoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Read-only scanner and controlled cleaner for DB/storage integrity mismatches:
 * 1. Orphaned historical file versions (latest_version = false with no active latest version).
 * 2. Active DB file records whose primary physical file is missing on disk.
 * 3. Orphaned physical files under storage/ not referenced by any active DB file record.
 */
public class StorageIntegrityService {
    private static final Logger log = LoggerFactory.getLogger(StorageIntegrityService.class);
    private static final String[] DERIVATIVE_SUFFIXES = new String[] { "", "_web", "_thumb", "_pdf" };

    public static final class DbFileIssue {
        public final String fileId;
        public final String fileName;
        public final String documentId;
        public final String documentTitle;
        public final String userId;
        public final String username;
        public final boolean latestVersion;
        public final Integer version;
        public final String versionLabel;
        public final long size;
        public final long createDate;
        public final boolean diskExists;
        public final String expectedPath;

        public DbFileIssue(String fileId, String fileName, String documentId, String documentTitle,
                           String userId, String username, boolean latestVersion, Integer version,
                           String versionLabel, long size, long createDate, boolean diskExists, String expectedPath) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.documentId = documentId;
            this.documentTitle = documentTitle;
            this.userId = userId;
            this.username = username;
            this.latestVersion = latestVersion;
            this.version = version;
            this.versionLabel = versionLabel;
            this.size = size;
            this.createDate = createDate;
            this.diskExists = diskExists;
            this.expectedPath = expectedPath;
        }
    }

    public static final class DiskOrphanIssue {
        public final String relativePath;
        public final long size;
        public final long lastModified;

        public DiskOrphanIssue(String relativePath, long size, long lastModified) {
            this.relativePath = relativePath;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    public static final class ScanResult {
        public final long scannedAt;
        public final int totalDbFiles;
        public final List<DbFileIssue> orphanedVersions;
        public final List<DbFileIssue> missingFiles;
        public final List<DiskOrphanIssue> orphanedDiskFiles;
        public final long orphanedDiskBytes;

        public ScanResult(long scannedAt, int totalDbFiles,
                          List<DbFileIssue> orphanedVersions,
                          List<DbFileIssue> missingFiles,
                          List<DiskOrphanIssue> orphanedDiskFiles,
                          long orphanedDiskBytes) {
            this.scannedAt = scannedAt;
            this.totalDbFiles = totalDbFiles;
            this.orphanedVersions = orphanedVersions;
            this.missingFiles = missingFiles;
            this.orphanedDiskFiles = orphanedDiskFiles;
            this.orphanedDiskBytes = orphanedDiskBytes;
        }
    }

    public static final class CleanResult {
        public int cleanedDbFiles;
        public int cleanedDiskFiles;
        public long reclaimedBytes;
        public int skipped;
    }

    /**
     * Performs a strictly read-only integrity scan across T_FILE and storage/.
     */
    public ScanResult scan() {
        FileDao fileDao = new FileDao();
        DocumentDao documentDao = new DocumentDao();
        UserDao userDao = new UserDao();

        List<File> allActiveFiles = new ArrayList<>();
        int offset = 0;
        List<File> batch;
        do {
            batch = fileDao.findAll(offset, 500);
            allActiveFiles.addAll(batch);
            offset += 500;
        } while (!batch.isEmpty());

        // Track which versionIds currently have an active latest_version = true record
        Set<String> activeLatestVersionIds = new HashSet<>();
        for (File f : allActiveFiles) {
            if (f.isLatestVersion() && !Strings.isNullOrEmpty(f.getVersionId())) {
                activeLatestVersionIds.add(f.getVersionId());
            }
        }

        Path baseDataDir = DirectoryUtil.getBaseDataDirectory().toAbsolutePath().normalize();
        Path storageRoot = baseDataDir.resolve("storage").toAbsolutePath().normalize();

        Map<String, String> docTitleCache = new HashMap<>();
        Map<String, String> usernameCache = new HashMap<>();
        Set<Path> knownActiveDiskPaths = new HashSet<>();

        List<DbFileIssue> orphanedVersions = new ArrayList<>();
        List<DbFileIssue> missingFiles = new ArrayList<>();

        for (File f : allActiveFiles) {
            Path storageDir = resolveExpectedStorageDirReadOnly(f, baseDataDir);
            String fileName = Strings.nullToEmpty(f.getName());
            Path primaryPath = fileName.isEmpty() ? null : storageDir.resolve(fileName).toAbsolutePath().normalize();

            boolean diskExists = primaryPath != null && Files.isRegularFile(primaryPath);
            if (!fileName.isEmpty()) {
                for (String suffix : DERIVATIVE_SUFFIXES) {
                    knownActiveDiskPaths.add(storageDir.resolve(fileName + suffix).toAbsolutePath().normalize());
                }
            }

            boolean isOrphanedVersion = !f.isLatestVersion()
                    && (Strings.isNullOrEmpty(f.getVersionId()) || !activeLatestVersionIds.contains(f.getVersionId()));

            if (isOrphanedVersion || !diskExists) {
                String docTitle = resolveDocTitle(f.getDocumentId(), documentDao, docTitleCache);
                String username = resolveUsername(f.getUserId(), userDao, usernameCache);
                String relExpected = primaryPath != null ? toRelativeSlashPath(baseDataDir, primaryPath) : "";
                long fileSize = (f.getSize() != null && f.getSize() > 0) ? f.getSize() : 0L;
                long createDate = f.getCreateDate() != null ? f.getCreateDate().getTime() : 0L;

                DbFileIssue issue = new DbFileIssue(
                        f.getId(),
                        fileName,
                        f.getDocumentId(),
                        docTitle,
                        f.getUserId(),
                        username,
                        f.isLatestVersion(),
                        f.getVersion(),
                        f.getVersionLabel(),
                        fileSize,
                        createDate,
                        diskExists,
                        relExpected
                );

                if (isOrphanedVersion) {
                    orphanedVersions.add(issue);
                } else {
                    missingFiles.add(issue);
                }
            }
        }

        // Walk storage/ for physical files not referenced by any active DB file
        List<DiskOrphanIssue> orphanedDiskFiles = new ArrayList<>();
        long orphanedDiskBytes = 0L;
        if (Files.isDirectory(storageRoot)) {
            try (Stream<Path> walk = Files.walk(storageRoot)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    Path normalized = p.toAbsolutePath().normalize();
                    if (!knownActiveDiskPaths.contains(normalized)) {
                        try {
                            long sz = Files.size(normalized);
                            long lm = Files.getLastModifiedTime(normalized).toMillis();
                            String rel = toRelativeSlashPath(storageRoot, normalized);
                            orphanedDiskFiles.add(new DiskOrphanIssue(rel, sz, lm));
                        } catch (IOException ignored) {
                        }
                    }
                });
            } catch (IOException e) {
                log.warn("Error scanning storage directory for orphaned disk files", e);
            }
        }
        orphanedDiskFiles.sort(Comparator.comparing(o -> o.relativePath));
        for (DiskOrphanIssue o : orphanedDiskFiles) {
            orphanedDiskBytes += o.size;
        }

        return new ScanResult(
                System.currentTimeMillis(),
                allActiveFiles.size(),
                orphanedVersions,
                missingFiles,
                orphanedDiskFiles,
                orphanedDiskBytes
        );
    }

    /**
     * Permanently cleans explicitly selected mismatched DB file records and/or orphaned disk files.
     * Re-validates every requested item against current DB/disk state to prevent accidental deletion.
     */
    public CleanResult clean(List<String> fileIds, List<String> diskPaths, String actorUserId) {
        CleanResult result = new CleanResult();
        ScanResult currentScan = scan();

        Set<String> cleanableFileIds = new HashSet<>();
        for (DbFileIssue issue : currentScan.orphanedVersions) {
            cleanableFileIds.add(issue.fileId);
        }
        for (DbFileIssue issue : currentScan.missingFiles) {
            cleanableFileIds.add(issue.fileId);
        }

        Set<String> cleanableDiskPaths = new HashSet<>();
        for (DiskOrphanIssue issue : currentScan.orphanedDiskFiles) {
            cleanableDiskPaths.add(issue.relativePath);
        }

        Path baseDataDir = DirectoryUtil.getBaseDataDirectory().toAbsolutePath().normalize();
        Path storageRoot = baseDataDir.resolve("storage").toAbsolutePath().normalize();
        FileDao fileDao = new FileDao();
        UserDao userDao = new UserDao();
        Map<String, Long> quotaDeltaByUser = new HashMap<>();

        if (fileIds != null) {
            Set<String> uniqueFileIds = new LinkedHashSet<>(fileIds);
            for (String fileId : uniqueFileIds) {
                if (Strings.isNullOrEmpty(fileId) || !cleanableFileIds.contains(fileId)) {
                    result.skipped++;
                    continue;
                }
                File file = fileDao.getActiveById(fileId);
                if (file == null) {
                    result.skipped++;
                    continue;
                }

                // Remove any leftover physical file and derivatives on disk
                Path storageDir = resolveExpectedStorageDirReadOnly(file, baseDataDir);
                String fileName = Strings.nullToEmpty(file.getName());
                if (!fileName.isEmpty()) {
                    for (String suffix : DERIVATIVE_SUFFIXES) {
                        Path candidate = storageDir.resolve(fileName + suffix).toAbsolutePath().normalize();
                        if (candidate.startsWith(storageRoot) && Files.isRegularFile(candidate)) {
                            try {
                                long sz = Files.size(candidate);
                                if (Files.deleteIfExists(candidate)) {
                                    result.reclaimedBytes += sz;
                                }
                            } catch (IOException e) {
                                log.warn("Failed to delete leftover file during integrity cleanup: " + candidate, e);
                            }
                        }
                    }
                    deleteEmptyDirectoriesUpwards(storageDir, storageRoot);
                }

                // Soft-delete in DB (not placed in trash since physical file/version is cleaned permanently)
                fileDao.delete(file.getId(), actorUserId);

                // Remove from Lucene index
                try {
                    AppContext.getInstance().getIndexingHandler().deleteDocument(file.getId());
                } catch (Exception e) {
                    log.warn("Failed to remove cleaned file from index: " + file.getId(), e);
                }

                // Track user quota adjustment
                if (file.getSize() != null && file.getSize() > 0 && !Strings.isNullOrEmpty(file.getUserId())) {
                    quotaDeltaByUser.merge(file.getUserId(), file.getSize(), Long::sum);
                }

                result.cleanedDbFiles++;
            }
        }

        // Apply user quota decrements safely (clamped >= 0)
        for (Map.Entry<String, Long> entry : quotaDeltaByUser.entrySet()) {
            User owner = userDao.getById(entry.getKey());
            if (owner != null) {
                long newCurrent = Math.max(0L, owner.getStorageCurrent() - entry.getValue());
                owner.setStorageCurrent(newCurrent);
                userDao.updateQuota(owner);
            }
        }

        // Clean selected orphaned disk files with strict containment and re-validation
        if (diskPaths != null) {
            Set<String> uniqueDiskPaths = new LinkedHashSet<>(diskPaths);
            for (String relPath : uniqueDiskPaths) {
                if (Strings.isNullOrEmpty(relPath)) {
                    continue;
                }
                String normalizedRel = relPath.replace('\\', '/');
                if (!cleanableDiskPaths.contains(normalizedRel)) {
                    result.skipped++;
                    continue;
                }
                Path target = storageRoot.resolve(normalizedRel).toAbsolutePath().normalize();
                // Defense-in-depth against path traversal (CWE-22)
                if (!target.startsWith(storageRoot) || target.equals(storageRoot)) {
                    result.skipped++;
                    continue;
                }
                if (!Files.isRegularFile(target)) {
                    result.skipped++;
                    continue;
                }
                try {
                    long sz = Files.size(target);
                    if (Files.deleteIfExists(target)) {
                        result.cleanedDiskFiles++;
                        result.reclaimedBytes += sz;
                        deleteEmptyDirectoriesUpwards(target.getParent(), storageRoot);
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete orphaned disk file: " + target, e);
                    result.skipped++;
                }
            }
        }

        return result;
    }

    private Path resolveExpectedStorageDirReadOnly(File file, Path baseDataDir) {
        if (!ConfigUtil.isFileEncrypt() && file != null) {
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
        return baseDataDir.resolve("storage").toAbsolutePath().normalize();
    }

    private String resolveDocTitle(String docId, DocumentDao documentDao, Map<String, String> cache) {
        if (Strings.isNullOrEmpty(docId)) {
            return null;
        }
        if (cache.containsKey(docId)) {
            return cache.get(docId);
        }
        Document doc = documentDao.getById(docId);
        String title = doc != null ? doc.getTitle() : null;
        cache.put(docId, title);
        return title;
    }

    private String resolveUsername(String userId, UserDao userDao, Map<String, String> cache) {
        if (Strings.isNullOrEmpty(userId)) {
            return null;
        }
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        User u = userDao.getById(userId);
        String name = u != null ? u.getUsername() : userId;
        cache.put(userId, name);
        return name;
    }

    private String toRelativeSlashPath(Path base, Path target) {
        try {
            return base.relativize(target).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return target.toString().replace('\\', '/');
        }
    }

    private void deleteEmptyDirectoriesUpwards(Path dir, Path stopAtExclusive) {
        Path current = dir != null ? dir.toAbsolutePath().normalize() : null;
        while (current != null && !current.equals(stopAtExclusive) && current.startsWith(stopAtExclusive)) {
            if (!Files.isDirectory(current)) {
                current = current.getParent();
                continue;
            }
            try (Stream<Path> entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    break;
                }
            } catch (IOException e) {
                break;
            }
            try {
                Files.deleteIfExists(current);
            } catch (IOException e) {
                break;
            }
            current = current.getParent();
        }
    }
}
