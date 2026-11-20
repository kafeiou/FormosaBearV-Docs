package com.sismics.docs.core.util.plugin;

import com.sismics.docs.core.util.DirectoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Safe .zip plugin package extraction and cleanup with Zip Slip protection.
 */
public class PluginPackageUtil {
    private static final long MAX_PACKAGE_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 2000;

    /**
     * Reads plugin.json from a .zip package and extracts all contents into /data/plugins/&lt;id&gt;/.
     */
    public static PluginManifest installPackage(InputStream zipStream) throws IOException {
        Path tempZip = Files.createTempFile("formosa_plugin_", ".zip");
        try {
            Files.copy(zipStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(tempZip) > MAX_PACKAGE_BYTES) {
                throw new IllegalArgumentException("Plugin package exceeds 50 MB limit");
            }

            // First pass: validate entries against Zip Slip and locate plugin.json
            PluginManifest manifest = null;
            int entryCount = 0;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    entryCount++;
                    if (entryCount > MAX_ENTRIES) {
                        throw new IllegalArgumentException("Plugin package contains too many entries");
                    }
                    validateEntryName(entry.getName());
                    if ("plugin.json".equals(normalizeEntryName(entry.getName())) && !entry.isDirectory()) {
                        manifest = PluginManifest.parse(readLimitedUtf8(zis, 256 * 1024));
                    }
                }
            }
            if (manifest == null) {
                throw new IllegalArgumentException("Plugin package is missing plugin.json at root");
            }

            // Extract atomically to staging dir then swap into /data/plugins/<id>
            Path pluginsDir = DirectoryUtil.getPluginDirectory().toAbsolutePath().normalize();
            Path targetDir = pluginsDir.resolve(manifest.getId()).normalize();
            Path stagingDir = pluginsDir.resolve(manifest.getId() + ".staging-" + System.nanoTime()).normalize();
            if (!targetDir.startsWith(pluginsDir) || !stagingDir.startsWith(pluginsDir)) {
                throw new IllegalArgumentException("Invalid plugin target path");
            }

            Files.createDirectories(stagingDir);
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    validateEntryName(entry.getName());
                    String cleanName = normalizeEntryName(entry.getName());
                    if (cleanName.isEmpty()) {
                        continue;
                    }
                    Path dest = stagingDir.resolve(cleanName).toAbsolutePath().normalize();
                    if (!dest.startsWith(stagingDir) || dest.equals(stagingDir)) {
                        if (dest.equals(stagingDir) && entry.isDirectory()) {
                            continue;
                        }
                        throw new IllegalArgumentException("Zip Slip detected: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                    } else {
                        Path parent = dest.getParent();
                        if (parent == null || !parent.startsWith(stagingDir)) {
                            throw new IllegalArgumentException("Zip Slip detected: " + entry.getName());
                        }
                        Files.createDirectories(parent);
                        Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (Exception e) {
                deleteDirectoryQuietly(stagingDir);
                throw e;
            }

            deleteDirectoryQuietly(targetDir);
            Files.move(stagingDir, targetDir, StandardCopyOption.REPLACE_EXISTING);
            return manifest;
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    public static void deletePluginFiles(String pluginId) {
        if (pluginId == null || pluginId.contains("..") || pluginId.contains("/") || pluginId.contains("\\")) {
            return;
        }
        Path pluginsDir = DirectoryUtil.getPluginDirectory().toAbsolutePath().normalize();
        Path targetDir = pluginsDir.resolve(pluginId).normalize();
        if (targetDir.startsWith(pluginsDir) && !targetDir.equals(pluginsDir)) {
            deleteDirectoryQuietly(targetDir);
        }
    }

    public static Path resolvePluginAsset(String pluginId, String relativePath) {
        if (pluginId == null || relativePath == null) {
            return null;
        }
        String cleanRel = relativePath.replace('\\', '/');
        while (cleanRel.startsWith("/")) {
            cleanRel = cleanRel.substring(1);
        }
        if (cleanRel.isEmpty() || cleanRel.contains("..")) {
            return null;
        }
        Path pluginRoot = DirectoryUtil.getPluginDirectory().resolve(pluginId).toAbsolutePath().normalize();
        Path asset = pluginRoot.resolve(cleanRel).normalize();
        if (!asset.startsWith(pluginRoot) || !Files.isRegularFile(asset)) {
            return null;
        }
        return asset;
    }

    private static void validateEntryName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Empty zip entry name");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("\\") || normalized.contains(":")) {
            throw new IllegalArgumentException("Absolute zip entry path is forbidden: " + name);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Zip Slip path traversal detected: " + name);
            }
        }
    }

    private static String normalizeEntryName(String name) {
        String n = name.replace('\\', '/');
        while (n.startsWith("./")) {
            n = n.substring(2);
        }
        return n;
    }

    private static String readLimitedUtf8(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        int read;
        while ((read = is.read(buf)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("plugin.json exceeds maximum allowed size");
            }
            baos.write(buf, 0, read);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
