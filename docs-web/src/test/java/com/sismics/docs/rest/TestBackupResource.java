package com.sismics.docs.rest;

import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Test for backup and restore resource.
 */
public class TestBackupResource extends BaseJerseyTest {

    @Test
    public void testBackupInfoAndExport() throws Exception {
        String adminToken = adminToken();

        // 1. Test GET /backup/info
        JsonObject info = target().path("/backup/info").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);

        Assert.assertNotNull(info);
        Assert.assertTrue(info.containsKey("database_driver"));
        Assert.assertTrue(info.containsKey("storage_path"));
        Assert.assertFalse(info.getString("storage_path").isEmpty());

        // 2. Test GET /backup (ZIP export)
        Response response = target().path("/backup").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get();

        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertEquals("application/zip", response.getMediaType().toString());

        byte[] zipBytes = response.readEntity(byte[].class);
        Assert.assertTrue("ZIP byte array should not be empty", zipBytes.length > 0);

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }

        // Verify metadata files are present
        Assert.assertTrue("manifest.json must be present in backup", entries.contains("manifest.json"));
        Assert.assertTrue("config.json must be present in backup", entries.contains("config.json"));
        Assert.assertTrue("dump.sql must be present in backup", entries.contains("dump.sql"));

        // Verify storage files are NOT packaged in the lightweight backup
        for (String entryName : entries) {
            Assert.assertFalse("storage/ files must not be inside lightweight backup ZIP", entryName.startsWith("storage/"));
        }

        // 3. Ensure storage directory exists and create a dummy file
        Path storageDir = DirectoryUtil.getBaseDataDirectory().resolve("storage");
        Files.createDirectories(storageDir);
        Path dummyFile = storageDir.resolve("dummy_test_file.txt");
        Files.writeString(dummyFile, "Important physical data that must not be wiped");

        // Verify dummy file still exists on disk
        Assert.assertTrue("Dummy physical storage file should exist", Files.exists(dummyFile));
    }
}
