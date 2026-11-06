package com.sismics.docs.core.service;

import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.event.RebuildIndexAsyncEvent;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.util.jpa.DialectUtil;
import com.sismics.util.jpa.EMF;
import jakarta.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service handling full system backup and restore.
 */
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final List<String> PREFERRED_TABLE_ORDER = Arrays.asList(
            "T_LOCALE",
            "T_BASE_FUNCTION",
            "T_ROLE",
            "T_ROLE_BASE_FUNCTION",
            "T_USER",
            "T_AUTHENTICATION_TOKEN",
            "T_CONFIG",
            "T_GROUP",
            "T_USER_GROUP",
            "T_VOCABULARY",
            "T_VOCABULARY_TERM",
            "T_CUSTOM_FIELD",
            "T_DOCUMENT",
            "T_CUSTOM_FIELD_VALUE",
            "T_TAG",
            "T_DOCUMENT_TAG",
            "T_FILE",
            "T_ACL",
            "T_SHARE",
            "T_AUDIT_LOG",
            "T_ROUTE_MODEL",
            "T_ROUTE_STEP_MODEL",
            "T_ROUTE",
            "T_ROUTE_STEP",
            "T_ROUTE_STEP_VALIDATOR",
            "T_WEBHOOK"
    );

    /**
     * Data Transfer Object for backup status information.
     */
    public static class BackupInfo {
        public String databaseDriver;
        public boolean fileEncrypt;
        public boolean fileEncryptLocked;
        public boolean fileDelete;
        public boolean fileDuplicate;
        public boolean fileDownload;
        public long documentCount;
        public long fileCount;
        public long storageSizeBytes;
    }

    /**
     * Gets current backup statistics and configuration status.
     */
    public BackupInfo getBackupInfo() {
        BackupInfo info = new BackupInfo();
        info.databaseDriver = EMF.isDriverPostgresql() ? "PostgreSQL" : (EMF.isDriverH2() ? "H2" : "Unknown");
        info.fileEncrypt = ConfigUtil.isFileEncrypt();
        info.fileEncryptLocked = ConfigUtil.isFileEncryptLocked();
        info.fileDelete = ConfigUtil.isFileDelete();
        info.fileDuplicate = ConfigUtil.canFileDuplicate();
        info.fileDownload = ConfigUtil.canFileDownload();

        Connection conn = null;
        try {
            conn = EMF.obtainConnection();
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("select count(*) from T_DOCUMENT where DOC_DELETEDATE_D is null")) {
                    if (rs.next()) {
                        info.documentCount = rs.getLong(1);
                    }
                }
                try (ResultSet rs = stmt.executeQuery("select count(*) from T_FILE where FIL_DELETEDATE_D is null")) {
                    if (rs.next()) {
                        info.fileCount = rs.getLong(1);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error gathering backup database counts", e);
        } finally {
            if (conn != null) {
                try {
                    EMF.releaseConnection(conn);
                } catch (Exception ignored) {
                }
            }
        }

        try {
            Path storageDir = DirectoryUtil.getBaseDataDirectory().resolve("storage");
            if (Files.exists(storageDir)) {
                final long[] totalSize = {0L};
                Files.walkFileTree(storageDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        totalSize[0] += attrs.size();
                        return FileVisitResult.CONTINUE;
                    }
                });
                info.storageSizeBytes = totalSize[0];
            }
        } catch (Exception e) {
            log.error("Error calculating storage directory size", e);
        }

        return info;
    }

    /**
     * Exports full system backup as a streaming ZIP archive.
     *
     * @param outputStream Output stream to write ZIP archive to.
     * @throws Exception If an export error occurs.
     */
    public void exportBackup(OutputStream outputStream) throws Exception {
        Connection conn = null;
        try {
            conn = EMF.obtainConnection();
            List<String> tables = getDatabaseTables(conn);

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(outputStream), StandardCharsets.UTF_8)) {
                // 1. Export manifest.json
                zos.putNextEntry(new ZipEntry("manifest.json"));
                JsonObject manifestJson = Json.createObjectBuilder()
                        .add("app", "FormosaBearV Docs")
                        .add("version", "2.02")
                        .add("exportDate", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()))
                        .add("databaseDriver", EMF.getDriver())
                        .build();
                zos.write(manifestJson.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 2. Export config.json
                zos.putNextEntry(new ZipEntry("config.json"));
                JsonObjectBuilder configMapBuilder = Json.createObjectBuilder();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("select CFG_ID_C, CFG_VALUE_C from T_CONFIG")) {
                    while (rs.next()) {
                        String k = rs.getString(1);
                        String v = rs.getString(2);
                        if (k != null && v != null) {
                            configMapBuilder.add(k, v);
                        }
                    }
                }
                JsonObject configJson = Json.createObjectBuilder()
                        .add("version", "2.02")
                        .add("fileEncrypt", ConfigUtil.isFileEncrypt())
                        .add("fileEncryptLocked", ConfigUtil.isFileEncryptLocked())
                        .add("fileDelete", ConfigUtil.isFileDelete())
                        .add("fileDuplicate", ConfigUtil.canFileDuplicate())
                        .add("fileDownload", ConfigUtil.canFileDownload())
                        .add("configs", configMapBuilder)
                        .build();
                zos.write(configJson.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 3. Export dump.sql
                zos.putNextEntry(new ZipEntry("dump.sql"));
                OutputStreamWriter sqlWriter = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
                sqlWriter.write("-- FormosaBearV / Teedy Standard ANSI SQL Dump\n");
                sqlWriter.write("-- Generated at " + new Date() + "\n");
                sqlWriter.write("!H2!SET REFERENTIAL_INTEGRITY FALSE;\n");
                sqlWriter.write("!PGSQL!SET session_replication_role = 'replica';\n\n");

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                for (String table : tables) {
                    exportTableSql(conn, table, sqlWriter, dateFormat);
                }

                sqlWriter.write("\n!H2!SET REFERENTIAL_INTEGRITY TRUE;\n");
                sqlWriter.write("!PGSQL!SET session_replication_role = 'DEFAULT';\n");
                sqlWriter.flush();
                zos.closeEntry();

                // 4. Export files under storage/ and delete/
                Path baseDir = DirectoryUtil.getBaseDataDirectory();
                packageDirectory(zos, baseDir, "storage");
                packageDirectory(zos, baseDir, "delete");

                zos.finish();
            }
        } finally {
            if (conn != null) {
                try {
                    EMF.releaseConnection(conn);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void packageDirectory(ZipOutputStream zos, Path baseDir, String subDirName) throws IOException {
        Path targetDir = baseDir.resolve(subDirName);
        if (!Files.exists(targetDir)) {
            return;
        }

        Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = baseDir.relativize(file);
                String entryName = relative.toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(attrs.lastModifiedTime().toMillis());
                entry.setSize(attrs.size());
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void exportTableSql(Connection conn, String table, Writer writer, SimpleDateFormat dateFormat) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select * from " + table)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            writer.write("-- Table: " + table + "\n");
            while (rs.next()) {
                StringBuilder sql = new StringBuilder("INSERT INTO ");
                sql.append(table).append(" (");
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) sql.append(", ");
                    sql.append(meta.getColumnName(i));
                }
                sql.append(") VALUES (");

                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) sql.append(", ");
                    Object obj = rs.getObject(i);
                    if (obj == null) {
                        sql.append("NULL");
                    } else if (obj instanceof Boolean) {
                        sql.append(((Boolean) obj) ? "TRUE" : "FALSE");
                    } else if (obj instanceof Number) {
                        sql.append(obj.toString());
                    } else if (obj instanceof Timestamp) {
                        sql.append("'").append(dateFormat.format((Timestamp) obj)).append("'");
                    } else if (obj instanceof java.sql.Date) {
                        sql.append("'").append(dateFormat.format((java.sql.Date) obj)).append("'");
                    } else if (obj instanceof byte[]) {
                        byte[] bytes = (byte[]) obj;
                        sql.append("X'");
                        for (byte b : bytes) {
                            sql.append(String.format("%02x", b));
                        }
                        sql.append("'");
                    } else {
                        String s = obj.toString();
                        sql.append("'").append(s.replace("'", "''")).append("'");
                    }
                }
                sql.append(");\n");
                writer.write(sql.toString());
            }
            writer.write("\n");
        } catch (Exception e) {
            log.warn("Error dumping table " + table + ", skipping: " + e.getMessage());
        }
    }

    /**
     * Restores full system backup from an uploaded ZIP archive input stream.
     *
     * @param zipInputStream Input stream of the backup ZIP archive.
     * @throws Exception If restore fails.
     */
    public void restoreBackup(InputStream zipInputStream) throws Exception {
        Path tempDir = Files.createTempDirectory("teedy_restore_");
        try {
            // Step 1: Unpack ZIP archive with Zip Slip path traversal security check
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipInputStream), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    Path destPath = tempDir.resolve(entry.getName()).normalize();
                    if (!destPath.startsWith(tempDir)) {
                        throw new SecurityException("Zip Slip path traversal detected: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.createDirectories(destPath.getParent());
                        try (OutputStream fos = Files.newOutputStream(destPath)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            Path dumpSqlFile = tempDir.resolve("dump.sql");
            Path configJsonFile = tempDir.resolve("config.json");
            if (!Files.exists(dumpSqlFile)) {
                throw new IllegalArgumentException("Backup archive is missing dump.sql!");
            }

            // Step 2: Database restore inside transactional connection
            Connection conn = null;
            try {
                conn = EMF.obtainConnection();
                conn.setAutoCommit(false);

                try (Statement stmt = conn.createStatement()) {
                    // Temporarily disable foreign keys
                    if (EMF.isDriverH2()) {
                        stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
                    } else if (EMF.isDriverPostgresql()) {
                        stmt.execute("SET session_replication_role = 'replica'");
                    }

                    // Clean all existing tables in reverse preferred order
                    List<String> tables = getDatabaseTables(conn);
                    for (int i = tables.size() - 1; i >= 0; i--) {
                        String t = tables.get(i);
                        try {
                            stmt.executeUpdate("DELETE FROM " + t);
                        } catch (Exception e) {
                            log.warn("Could not delete from table " + t + ": " + e.getMessage());
                        }
                    }

                    // Execute dump.sql statement by statement
                    try (BufferedReader reader = Files.newBufferedReader(dumpSqlFile, StandardCharsets.UTF_8)) {
                        StringBuilder currentStmt = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                                continue;
                            }

                            String transformed = DialectUtil.transform(trimmed);
                            if (transformed == null) {
                                continue;
                            }

                            currentStmt.append(" ").append(transformed);
                            if (transformed.endsWith(";")) {
                                String sql = currentStmt.toString().trim();
                                if (sql.endsWith(";")) {
                                    sql = sql.substring(0, sql.length() - 1);
                                }
                                if (!sql.isEmpty()) {
                                    stmt.execute(sql);
                                }
                                currentStmt.setLength(0);
                            }
                        }
                    }

                    // Re-enable foreign keys
                    if (EMF.isDriverH2()) {
                        stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
                    } else if (EMF.isDriverPostgresql()) {
                        stmt.execute("SET session_replication_role = 'DEFAULT'");
                    }

                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw new RuntimeException("Database restore failed: " + e.getMessage(), e);
                }
            } finally {
                if (conn != null) {
                    try {
                        EMF.releaseConnection(conn);
                    } catch (Exception ignored) {
                    }
                }
            }

            // Step 3: Apply config.json settings to ensure full sync
            if (Files.exists(configJsonFile)) {
                try (InputStream fis = Files.newInputStream(configJsonFile);
                     JsonReader jsonReader = Json.createReader(fis)) {
                    JsonObject root = jsonReader.readObject();
                    if (root.containsKey("configs")) {
                        JsonObject configs = root.getJsonObject("configs");
                        Connection cfgConn = null;
                        try {
                            cfgConn = EMF.obtainConnection();
                            cfgConn.setAutoCommit(true);
                            try (PreparedStatement updateStmt = cfgConn.prepareStatement(
                                    "UPDATE T_CONFIG SET CFG_VALUE_C = ? WHERE CFG_ID_C = ?");
                                 PreparedStatement insertStmt = cfgConn.prepareStatement(
                                    "INSERT INTO T_CONFIG (CFG_ID_C, CFG_VALUE_C) VALUES (?, ?)")) {
                                for (Map.Entry<String, JsonValue> entry : configs.entrySet()) {
                                    String key = entry.getKey();
                                    String val = entry.getValue() instanceof JsonString ?
                                            ((JsonString) entry.getValue()).getString() :
                                            entry.getValue().toString();

                                    updateStmt.setString(1, val);
                                    updateStmt.setString(2, key);
                                    int rows = updateStmt.executeUpdate();
                                    if (rows == 0) {
                                        insertStmt.setString(1, key);
                                        insertStmt.setString(2, val);
                                        insertStmt.executeUpdate();
                                    }
                                }
                            }
                        } finally {
                            if (cfgConn != null) {
                                EMF.releaseConnection(cfgConn);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not apply config.json: " + e.getMessage());
                }
            }
            ConfigUtil.resetCache();

            // Step 4: Restore storage/ and delete/ directories (Full Clean Restore)
            Path baseDir = DirectoryUtil.getBaseDataDirectory();
            Path targetStorage = baseDir.resolve("storage");
            Path targetDelete = baseDir.resolve("delete");

            // Clean existing storage and delete directories
            deleteDirectoryIfExists(targetStorage);
            deleteDirectoryIfExists(targetDelete);

            // Copy restored files from temp folder
            Path tempStorage = tempDir.resolve("storage");
            if (Files.exists(tempStorage)) {
                copyDirectoryRecursively(tempStorage, targetStorage);
            }

            Path tempDelete = tempDir.resolve("delete");
            if (Files.exists(tempDelete)) {
                copyDirectoryRecursively(tempDelete, targetDelete);
            }

            // Step 5: Clean old Lucene index and trigger full asynchronous reindex
            try {
                Path luceneDir = DirectoryUtil.getLuceneDirectory();
                deleteDirectoryIfExists(luceneDir);
            } catch (Exception e) {
                log.warn("Could not clean old lucene directory", e);
            }
            AppContext.getInstance().getAsyncEventBus().post(new RebuildIndexAsyncEvent());

        } finally {
            // Clean up temporary extracted folder
            deleteDirectoryIfExists(tempDir);
        }
    }

    private static void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectoryIfExists(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("Could not completely delete directory: " + path, e);
        }
    }

    private List<String> getDatabaseTables(Connection conn) {
        List<String> result = new ArrayList<>();
        Set<String> discovered = new HashSet<>();

        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null) {
                        discovered.add(name.toUpperCase());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query DatabaseMetaData tables: " + e.getMessage());
        }

        for (String pref : PREFERRED_TABLE_ORDER) {
            if (discovered.isEmpty() || discovered.contains(pref)) {
                result.add(pref);
            }
        }

        // Add any discovered T_* tables not already in the preferred list
        for (String d : discovered) {
            if (d.startsWith("T_") && !result.contains(d)) {
                result.add(d);
            }
        }

        return result;
    }
}
