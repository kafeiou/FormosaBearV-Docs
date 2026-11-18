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
}
