package com.sismics.docs.rest;

import java.io.InputStream;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.sismics.util.filter.TokenBasedSecurityFilter;

/**
 * Exhaustive test of the share resource.
 * 
 * @author bgamard
 */
public class TestShareResource extends BaseJerseyTest {
    /**
     * Test the share resource.
     * 
     * @throws Exception
     */
    @Test
    public void testShareResource() throws Exception {
        // Login share1
        clientUtil.createUser("share1");
        String share1Token = clientUtil.login("share1");
        
        // Create a document
        JsonObject json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, share1Token)
                .put(Entity.form(new Form()
                        .param("title", "File test document 1")
                        .param("language", "eng")), JsonObject.class);
        String document1Id = json.getString("id");
        Assert.assertNotNull(document1Id);
        
        // Add a file
        String file1Id;
        try (InputStream is = Resources.getResource("file/PIA00452.jpg").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "PIA00452.jpg");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, share1Token)
                        .put(Entity.entity(multiPart.field("id", document1Id).bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file1Id = json.getString("id");
            }
        }
        
        // Share this document (default allow_export must be false -> preview only)
        json = target().path("/share").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, share1Token)
                .put(Entity.form(new Form()
                        .param("id", document1Id)
                        .param("name", "4 All")), JsonObject.class);
        String share1Id = json.getString("id");
        Assert.assertFalse("New share must default to allow_export = false", json.getBoolean("allow_export"));
        
        // Get the document anonymously
        json = target().path("/document/" + document1Id)
                .queryParam("share", share1Id)
                .request()
                .get(JsonObject.class);
        Assert.assertEquals(document1Id, json.getString("id"));
        Assert.assertFalse(json.getBoolean("share_allow_export"));
        Assert.assertFalse(json.getBoolean("can_download"));
        Assert.assertEquals(3, json.getJsonArray("acls").size()); // 2 for the creator, 1 for the share

        // Get all comments from this document anonymously
        json = target().path("/comment/" + document1Id)
                .queryParam("share", share1Id)
                .request()
                .get(JsonObject.class);
        JsonArray comments = json.getJsonArray("comments");
        Assert.assertEquals(0, comments.size());
        
        // Get all files from this document anonymously
        json = target().path("/file/list")
                .queryParam("id", document1Id)
                .queryParam("share", share1Id)
                .request()
                .get(JsonObject.class);
        JsonArray files = json.getJsonArray("files");
        Assert.assertEquals(1, files.size());

        // Preview (size=web and size=thumb) must succeed even when allow_export = false
        Response previewResp = target().path("/file/" + file1Id + "/data")
                .queryParam("size", "web")
                .queryParam("share", share1Id)
                .request()
                .get();
        Assert.assertEquals(Status.OK.getStatusCode(), previewResp.getStatus());

        // Raw file download, ZIP export, and PDF export must be forbidden (403) when allow_export = false
        Response forbiddenDataResp = target().path("/file/" + file1Id + "/data")
                .queryParam("share", share1Id)
                .request()
                .get();
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), forbiddenDataResp.getStatus());

        Response forbiddenZipResp = target().path("/file/zip")
                .queryParam("id", document1Id)
                .queryParam("share", share1Id)
                .request()
                .get();
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), forbiddenZipResp.getStatus());

        Response forbiddenPdfResp = target().path("/document/" + document1Id + "/pdf")
                .queryParam("share", share1Id)
                .queryParam("metadata", false)
                .queryParam("fitimagetopage", true)
                .queryParam("margin", 10)
                .request()
                .get();
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), forbiddenPdfResp.getStatus());

        // Enable allow_export on the share via POST /share/{id}
        JsonObject updateShareJson = target().path("/share/" + share1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, share1Token)
                .post(Entity.form(new Form().param("allow_export", "true")), JsonObject.class);
        Assert.assertEquals("ok", updateShareJson.getString("status"));
        Assert.assertTrue(updateShareJson.getBoolean("allow_export"));

        // Now raw file download, ZIP, and PDF export succeed anonymously
        Response response = target().path("/file/" + file1Id + "/data")
                .queryParam("thumbnail", false)
                .queryParam("share", share1Id)
                .request()
                .get();
        Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
        InputStream is = (InputStream) response.getEntity();
        byte[] fileBytes = ByteStreams.toByteArray(is);
        Assert.assertEquals(163510, fileBytes.length);

        Response okZipResp = target().path("/file/zip")
                .queryParam("id", document1Id)
                .queryParam("share", share1Id)
                .request()
                .get();
        Assert.assertEquals(Status.OK.getStatusCode(), okZipResp.getStatus());

        Response okPdfResp = target().path("/document/" + document1Id + "/pdf")
                .queryParam("share", share1Id)
                .queryParam("metadata", false)
                .queryParam("fitimagetopage", true)
                .queryParam("margin", 10)
                .request()
                .get();
        Assert.assertEquals(Status.OK.getStatusCode(), okPdfResp.getStatus());
        
        // Deletes the share (not allowed)
        clientUtil.createUser("share2");
        String share2Token = clientUtil.login("share2");
        response = target().path("/share/" + share1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, share2Token)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("DocumentNotFound", json.getString("type"));
        
        // Deletes the share
        json = target().path("/share/" + share1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, share1Token)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));

        // Deletes the share again
        response = target().path("/share/" + share1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, share1Token)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ShareNotFound", json.getString("type"));

        // Deletes share1 and share 2
        String adminToken = adminToken();
        target().path("/user/share1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        target().path("/user/share2").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }

    /**
     * Regression test for CVE-2026-50885: passing user/group/forged IDs as ?share= must not bypass ACLs.
     */
    @Test
    public void testShareSpoofingBlocked() throws Exception {
        clientUtil.createUser("share_spoof_owner");
        String ownerToken = clientUtil.login("share_spoof_owner");

        String docId = clientUtil.createDocument(ownerToken);
        String fileId = clientUtil.addFileToDocument(FILE_PIA_00452_JPG, ownerToken, docId);

        // Fetch owner's user ID from the document's creator_id
        JsonObject docJson = target().path("/document/" + docId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, ownerToken)
                .get(JsonObject.class);
        String ownerUserId = docJson.getString("creator_id");

        // Unauthenticated requests spoofing share=<ownerUserId> or share=administrators must be rejected
        for (String spoofedShare : new String[]{ ownerUserId, "administrators", "non-existent-share-id" }) {
            Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(
                    target().path("/document/" + docId)
                            .queryParam("share", spoofedShare)
                            .request().get().getStatus()));

            Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(
                    target().path("/file/list")
                            .queryParam("id", docId)
                            .queryParam("share", spoofedShare)
                            .request().get().getStatus()));

            Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(
                    target().path("/file/" + fileId + "/data")
                            .queryParam("share", spoofedShare)
                            .request().get().getStatus()));

            Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(
                    target().path("/comment/" + docId)
                            .queryParam("share", spoofedShare)
                            .request().get().getStatus()));
        }

        String adminToken = adminToken();
        target().path("/user/share_spoof_owner").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }

    /**
     * Test GET /share/list (mine & admin all), offboarding transfer inheritance of share management,
     * and admin management of any user's share.
     */
    @Test
    public void testShareListAdminAndOffboardingTransfer() throws Exception {
        String adminToken = adminToken();
        clientUtil.createUser("share_alice");
        clientUtil.createUser("share_bob");
        String aliceToken = clientUtil.login("share_alice");
        String bobToken = clientUtil.login("share_bob");

        // Alice creates a document and shares it
        String aliceDocId = clientUtil.createDocument(aliceToken);
        JsonObject aliceShare = target().path("/share").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceToken)
                .put(Entity.form(new Form()
                        .param("id", aliceDocId)
                        .param("name", "Alice Public Link")), JsonObject.class);
        String aliceShareId = aliceShare.getString("id");

        // Alice sees her share in GET /share/list
        JsonObject aliceList = target().path("/share/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, aliceList.getJsonArray("shares").size());
        Assert.assertEquals(aliceShareId, aliceList.getJsonArray("shares").getJsonObject(0).getString("id"));
        Assert.assertEquals("share_alice", aliceList.getJsonArray("shares").getJsonObject(0).getString("owner"));

        // Bob does NOT see Alice's share yet and cannot request scope=all
        JsonObject bobListBefore = target().path("/share/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .get(JsonObject.class);
        Assert.assertEquals(0, bobListBefore.getJsonArray("shares").size());

        Response bobAllForbidden = target().path("/share/list").queryParam("scope", "all").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .get();
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(bobAllForbidden.getStatus()));

        // Admin sees Alice's share via scope=all
        JsonObject adminAll = target().path("/share/list").queryParam("scope", "all").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        boolean foundForAdmin = false;
        for (int i = 0; i < adminAll.getJsonArray("shares").size(); i++) {
            if (aliceShareId.equals(adminAll.getJsonArray("shares").getJsonObject(i).getString("id"))) {
                foundForAdmin = true;
            }
        }
        Assert.assertTrue("Admin should see all users' shares", foundForAdmin);

        // Offboarding transfer: Alice -> Bob
        Response transferResp = target().path("/user/share_alice/transfer").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("target_username", "share_bob")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(transferResp.getStatus()));

        // Bob now sees the transferred document's share and can manage it
        JsonObject bobListAfter = target().path("/share/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, bobListAfter.getJsonArray("shares").size());
        Assert.assertEquals(aliceShareId, bobListAfter.getJsonArray("shares").getJsonObject(0).getString("id"));
        Assert.assertEquals("share_bob", bobListAfter.getJsonArray("shares").getJsonObject(0).getString("owner"));

        JsonObject bobToggle = target().path("/share/" + aliceShareId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .post(Entity.form(new Form().param("allow_export", "true")), JsonObject.class);
        Assert.assertTrue(bobToggle.getBoolean("allow_export"));

        // Admin can also toggle and revoke any user's share directly
        JsonObject adminToggle = target().path("/share/" + aliceShareId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("allow_export", "false")), JsonObject.class);
        Assert.assertFalse(adminToggle.getBoolean("allow_export"));

        JsonObject adminRevoke = target().path("/share/" + aliceShareId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", adminRevoke.getString("status"));

        // Cleanup users
        target().path("/user/share_alice").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        target().path("/user/share_bob").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }
}