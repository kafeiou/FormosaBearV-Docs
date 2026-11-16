package com.sismics.docs.rest;

import com.sismics.util.filter.TokenBasedSecurityFilter;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

/**
 * Test the Trash / Recycle Bin resource.
 */
public class TestTrashResource extends BaseJerseyTest {

    @Test
    public void testTrashLifecycleAndGuardrails() throws Exception {
        String adminToken = adminToken();
        target().path("/app/file_delete").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("enabled", "false")));

        String uniqueUser = "trashuser_" + System.currentTimeMillis();
        clientUtil.createUser(uniqueUser);
        String user1Token = clientUtil.login(uniqueUser);

        // 1. Create a document and attach a file
        long create1Date = new Date().getTime();
        JsonObject docJson = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .put(Entity.form(new Form()
                        .param("title", "Trash Test Document 1")
                        .param("language", "eng")
                        .param("create_date", Long.toString(create1Date))), JsonObject.class);
        String doc1Id = docJson.getString("id");
        Assert.assertNotNull(doc1Id);

        String file1Id = clientUtil.addFileToDocument("file/PIA00452.jpg", user1Token, doc1Id);
        String file2Id = clientUtil.addFileToDocument("file/document.txt", user1Token, doc1Id);

        // 2. Soft-delete file1 individually first
        Response delFileResp = target().path("/file/" + file1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(delFileResp.getStatus()));

        // Wait briefly so timestamps are distinct before deleting parent document
        Thread.sleep(1100);

        // Check Trash: file1 should be in files, documents should be empty
        JsonObject trashJson = target().path("/trash").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .get(JsonObject.class);
        Assert.assertEquals(0, trashJson.getJsonArray("documents").size());
        Assert.assertEquals(1, trashJson.getJsonArray("files").size());
        Assert.assertEquals(file1Id, trashJson.getJsonArray("files").getJsonObject(0).getString("id"));
        Assert.assertFalse(trashJson.getJsonArray("files").getJsonObject(0).getBoolean("document_deleted"));

        // 3. Soft-delete parent document doc1 (which still has active file2)
        Response delDocResp = target().path("/document/" + doc1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(delDocResp.getStatus()));

        // Verify doc1 is in Trash documents (with file_count = 1 for file2), and file1 is still in Trash files (with document_deleted = true)
        trashJson = target().path("/trash").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .get(JsonObject.class);
        JsonArray docsArr = trashJson.getJsonArray("documents");
        JsonArray filesArr = trashJson.getJsonArray("files");
        Assert.assertEquals(1, docsArr.size());
        Assert.assertEquals(doc1Id, docsArr.getJsonObject(0).getString("id"));
        Assert.assertEquals(1, docsArr.getJsonObject(0).getInt("file_count"));
        Assert.assertEquals(1, filesArr.size());
        Assert.assertEquals(file1Id, filesArr.getJsonObject(0).getString("id"));
        Assert.assertTrue(filesArr.getJsonObject(0).getBoolean("document_deleted"));

        // 4. Guardrail test: trying to restore file1 while parent doc1 is still in Trash should return 400 ParentDocumentInTrash
        Response restoreFileWhileDocDeleted = target().path("/trash/file/" + file1Id + "/restore").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(restoreFileWhileDocDeleted.getStatus()));
        JsonObject errJson = restoreFileWhileDocDeleted.readEntity(JsonObject.class);
        Assert.assertEquals("ParentDocumentInTrash", errJson.getString("type"));

        // 5. Restore parent document doc1
        Response restoreDocResp = target().path("/trash/document/" + doc1Id + "/restore").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(restoreDocResp.getStatus()));

        // Verify doc1 is active again and has file2 restored
        JsonObject restoredDoc = target().path("/document/" + doc1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .get(JsonObject.class);
        Assert.assertEquals("Trash Test Document 1", restoredDoc.getString("title"));

        // 6. Now restore file1 (since parent doc1 is active again)
        Response restoreFileResp = target().path("/trash/file/" + file1Id + "/restore").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(restoreFileResp.getStatus()));

        // Trash should now be completely empty
        trashJson = target().path("/trash").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .get(JsonObject.class);
        Assert.assertEquals(0, trashJson.getJsonArray("documents").size());
        Assert.assertEquals(0, trashJson.getJsonArray("files").size());

        // Both files should be present on doc1
        JsonObject fileListJson = target().path("/file/list").queryParam("id", doc1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .get(JsonObject.class);
        Assert.assertEquals(2, fileListJson.getJsonArray("files").size());

        // 7. Test permanent purge and Empty Trash
        target().path("/file/" + file1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .delete();
        Response purgeFileResp = target().path("/trash/file/" + file1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(purgeFileResp.getStatus()));

        target().path("/document/" + doc1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .delete();
        Response emptyTrashResp = target().path("/trash/empty").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(emptyTrashResp.getStatus()));

        trashJson = target().path("/trash").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, user1Token)
                .get(JsonObject.class);
        Assert.assertEquals(0, trashJson.getJsonArray("documents").size());
        Assert.assertEquals(0, trashJson.getJsonArray("files").size());
    }
}
