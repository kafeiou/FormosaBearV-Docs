package com.sismics.docs.rest;

import com.sismics.util.filter.TokenBasedSecurityFilter;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exhaustive test for the plugin platform resource.
 */
public class TestPluginResource extends BaseJerseyTest {

    private byte[] buildPluginZip(String id, String name, String version, boolean zipSlip) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            if (zipSlip) {
                zos.putNextEntry(new ZipEntry("../evil.txt"));
                zos.write("bad".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            String manifest = "{\n"
                    + "  \"id\": \"" + id + "\",\n"
                    + "  \"name\": \"" + name + "\",\n"
                    + "  \"version\": \"" + version + "\",\n"
                    + "  \"description\": \"Test plugin\",\n"
                    + "  \"entry\": \"ui/index.html\",\n"
                    + "  \"nav_label\": \"" + name + "\",\n"
                    + "  \"nav_icon\": \"fas fa-puzzle-piece\",\n"
                    + "  \"protected_tags\": [\n"
                    + "    { \"name\": \"PluginRootTag\", \"color\": \"#3a87ad\", \"role\": \"PLUGIN_ROOT\" },\n"
                    + "    { \"name\": \"PluginMgmtTag\", \"color\": \"#d9534f\", \"role\": \"MANAGEMENT\" }\n"
                    + "  ]\n"
                    + "}\n";
            zos.putNextEntry(new ZipEntry("plugin.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("ui/index.html"));
            zos.write(("<html><body>Plugin v" + version + "</body></html>").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private Response uploadZip(String token, byte[] zipBytes) {
        StreamDataBodyPart filePart = new StreamDataBodyPart(
                "file", new ByteArrayInputStream(zipBytes), "plugin.zip", MediaType.APPLICATION_OCTET_STREAM_TYPE);
        FormDataMultiPart multiPart = new FormDataMultiPart();
        multiPart.bodyPart(filePart);
        return target().path("/plugin/upload").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .post(Entity.entity(multiPart, multiPart.getMediaType()));
    }

    @Test
    public void testPluginLifecycleProtectedTagsAndSecretMasking() throws Exception {
        String adminToken = adminToken();
        clientUtil.createUser("plugin_user1");
        String userToken = clientUtil.login("plugin_user1");

        // 1. Zip Slip package is rejected
        Response badResp = uploadZip(adminToken, buildPluginZip("sample-plug", "Sample", "1.0.0", true));
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), badResp.getStatus());

        // 2. Upload valid plugin v1.0.0 (starts disabled)
        Response upResp = uploadZip(adminToken, buildPluginZip("sample-plug", "Sample Plugin", "1.0.0", false));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), upResp.getStatus());
        JsonObject installed = upResp.readEntity(JsonObject.class);
        Assert.assertEquals("sample-plug", installed.getString("id"));
        Assert.assertEquals("1.0.0", installed.getString("version"));
        Assert.assertFalse(installed.getBoolean("enabled"));

        JsonArray protectedTags = installed.getJsonArray("protected_tags");
        Assert.assertEquals(2, protectedTags.size());
        String rootTagId = protectedTags.getJsonObject(0).getString("tag_id");

        // 3. Disabled plugin cannot be accessed by normal user, and protected tag cannot be deleted even while disabled
        Response assetDenied = target().path("/plugin/sample-plug/assets/ui/index.html").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get();
        Assert.assertEquals(Response.Status.FORBIDDEN.getStatusCode(), assetDenied.getStatus());

        Response delTagDisabled = target().path("/tag/" + rootTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), delTagDisabled.getStatus());
        Assert.assertEquals("ProtectedPluginTag", delTagDisabled.readEntity(JsonObject.class).getString("type"));

        // 4. Enable plugin -> visible in /plugin/enabled and asset accessible
        JsonObject enResp = target().path("/plugin/sample-plug/enable").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new jakarta.ws.rs.core.Form()), JsonObject.class);
        Assert.assertTrue(enResp.getBoolean("enabled"));

        JsonObject enabledList = target().path("/plugin/enabled").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, enabledList.getJsonArray("plugins").size());

        Response assetOk = target().path("/plugin/sample-plug/assets/ui/index.html").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get();
        Assert.assertEquals(Response.Status.OK.getStatusCode(), assetOk.getStatus());
        Assert.assertTrue(assetOk.readEntity(String.class).contains("Plugin v1.0.0"));

        // 5. Generic T_PLUGIN_DATA masks _secret and correct_* fields for non-admins
        target().path("/plugin/sample-plug/data/exams/q1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.json("{\"prompt\":\"1+1=?\",\"_secret\":\"hidden\",\"correct_answer\":\"2\"}"));

        JsonObject userData = target().path("/plugin/sample-plug/data/exams").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        String valStr = userData.getJsonArray("items").getJsonObject(0).getString("value");
        try (JsonReader jr = Json.createReader(new StringReader(valStr))) {
            JsonObject parsed = jr.readObject();
            Assert.assertEquals("1+1=?", parsed.getString("prompt"));
            Assert.assertFalse(parsed.containsKey("_secret"));
            Assert.assertFalse(parsed.containsKey("correct_answer"));
        }

        // 6. Upgrade to v1.1.0 preserves enabled=true
        Response upgResp = uploadZip(adminToken, buildPluginZip("sample-plug", "Sample Plugin", "1.1.0", false));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), upgResp.getStatus());
        JsonObject upgraded = upgResp.readEntity(JsonObject.class);
        Assert.assertEquals("1.1.0", upgraded.getString("version"));
        Assert.assertTrue(upgraded.getBoolean("enabled"));

        // 7. Uninstall unlocks protected tag so it can now be deleted manually
        Response uninst = target().path("/plugin/sample-plug").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Response.Status.OK.getStatusCode(), uninst.getStatus());

        Response delTagUnlocked = target().path("/tag/" + rootTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Response.Status.OK.getStatusCode(), delTagUnlocked.getStatus());
    }

    @Test
    public void testBundledPluginSeededOnStartup() throws Exception {
        java.nio.file.Files.deleteIfExists(
                com.sismics.docs.core.util.DirectoryUtil.getStorageDirectory(null).resolve("plugin-online-exam.zip"));
        String adminToken = adminToken();
        clientUtil.createUser("plugin_seed_viewer");
        String userToken = clientUtil.login("plugin_seed_viewer");

        // 0. Before choosing encryption mode, file_encrypt_locked must remain false and plugins doc is not seeded yet
        JsonObject appBefore = target().path("/app").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertFalse(appBefore.getBoolean("file_encrypt_locked"));

        // Lock file encryption mode (enabled=true) -> triggers bundled plugin seed with encryption
        Response lockResp = target().path("/app/file_encrypt").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new jakarta.ws.rs.core.Form().param("enabled", "true")));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), lockResp.getStatus());

        JsonObject appAfter = target().path("/app").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertTrue(appAfter.getBoolean("file_encrypt"));
        Assert.assertTrue(appAfter.getBoolean("file_encrypt_locked"));

        // 1. Admin sees the non-hidden "plugins" tag
        JsonObject adminTags = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String pluginsTagId = null;
        for (int i = 0; i < adminTags.getJsonArray("tags").size(); i++) {
            JsonObject t = adminTags.getJsonArray("tags").getJsonObject(i);
            if ("plugins".equals(t.getString("name"))) {
                pluginsTagId = t.getString("id");
                Assert.assertFalse(t.getBoolean("hidden"));
            }
        }
        Assert.assertNotNull("Expected 'plugins' tag for admin", pluginsTagId);

        // 2. Non-admin does NOT see the "plugins" tag
        JsonObject userTags = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        for (int i = 0; i < userTags.getJsonArray("tags").size(); i++) {
            Assert.assertNotEquals("plugins", userTags.getJsonArray("tags").getJsonObject(i).getString("name"));
        }

        // 3. Admin sees the "plugins" document tagged with "plugins" and with file usage description
        JsonObject adminDocs = target().path("/document/list")
                .queryParam("search", "tag:plugins")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String pluginsDocId = null;
        JsonArray docs = adminDocs.getJsonArray("documents");
        for (int i = 0; i < docs.size(); i++) {
            JsonObject d = docs.getJsonObject(i);
            if ("plugins".equals(d.getString("title"))) {
                pluginsDocId = d.getString("id");
            }
        }
        Assert.assertNotNull("Expected 'plugins' document for admin", pluginsDocId);

        JsonObject docDetail = target().path("/document/" + pluginsDocId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String description = docDetail.getString("description", "");
        Assert.assertTrue("Expected plugin-online-exam.zip in description", description.contains("plugin-online-exam.zip"));
        Assert.assertTrue("Expected Online Exam in description", description.contains("Online Exam"));

        // 4. Non-admin cannot see or read the "plugins" document
        JsonObject userDocs = target().path("/document/list")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        for (int i = 0; i < userDocs.getJsonArray("documents").size(); i++) {
            Assert.assertNotEquals(pluginsDocId, userDocs.getJsonArray("documents").getJsonObject(i).getString("id"));
        }
        Response userDocResp = target().path("/document/" + pluginsDocId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get();
        Assert.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), userDocResp.getStatus());

        // 5. "plugins" document contains plugin-online-exam.zip and it is downloadable
        JsonObject filesResp = target().path("/file/list")
                .queryParam("id", pluginsDocId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray files = filesResp.getJsonArray("files");
        String examFileId = null;
        for (int i = 0; i < files.size(); i++) {
            JsonObject f = files.getJsonObject(i);
            if ("plugin-online-exam.zip".equals(f.getString("name"))) {
                examFileId = f.getString("id");
            }
        }
        Assert.assertNotNull("Expected plugin-online-exam.zip in plugins document", examFileId);

        Response downloadResp = target().path("/file/" + examFileId + "/data").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get();
        Assert.assertEquals(Response.Status.OK.getStatusCode(), downloadResp.getStatus());
        byte[] zipBytes = downloadResp.readEntity(byte[].class);
        Assert.assertTrue(zipBytes.length > 100);
        Assert.assertEquals((byte) 'P', zipBytes[0]);
        Assert.assertEquals((byte) 'K', zipBytes[1]);

        // 6. Re-running seed is idempotent and does not duplicate files
        int fileCountBefore = files.size();
        com.sismics.docs.core.util.plugin.PluginSeedService.seedBundledPlugins();
        JsonObject filesAfter = target().path("/file/list")
                .queryParam("id", pluginsDocId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(fileCountBefore, filesAfter.getJsonArray("files").size());
    }
}
