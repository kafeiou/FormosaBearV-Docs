package com.sismics.docs.rest;

import com.sismics.util.filter.TokenBasedSecurityFilter;
import org.junit.Assert;
import org.junit.Test;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Test the tag resource.
 * 
 * @author bgamard
 */
public class TestTagResource extends BaseJerseyTest {
    /**
     * Test the tag resource.
     */
    @Test
    public void testTagResource() {
        // Login tag1
        clientUtil.createUser("tag1");
        String tag1Token = clientUtil.login("tag1");

        // Create a tag with a wrong name
        Response response = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .put(Entity.form(new Form()
                        .param("name", "Tag:3")
                        .param("color", "#ff0000")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));

        // Create a tag with a wrong name
        response = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .put(Entity.form(new Form()
                        .param("name", "Tag 3")
                        .param("color", "#ff0000")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));

        // Create a tag
        JsonObject json = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .put(Entity.form(new Form()
                        .param("name", "Tag3")
                        .param("color", "#ff0000")), JsonObject.class);
        String tag3Id = json.getString("id");
        Assert.assertNotNull(tag3Id);
        
        // Create a tag
        json = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .put(Entity.form(new Form()
                        .param("name", "Tag4")
                        .param("color", "#00ff00")
                        .param("parent", tag3Id)), JsonObject.class);
        String tag4Id = json.getString("id");
        Assert.assertNotNull(tag4Id);

        // Create a circular reference
        response = target().path("/tag/" + tag3Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .post(Entity.form(new Form()
                        .param("name", "Tag3")
                        .param("color", "#0000ff")
                        .param("parent", tag4Id)));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));

        // Get the tag
        json = target().path("/tag/" + tag4Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        Assert.assertEquals("Tag4", json.getString("name"));
        Assert.assertEquals("tag1", json.getString("creator"));
        Assert.assertEquals("#00ff00", json.getString("color"));
        Assert.assertEquals(tag3Id, json.getString("parent"));
        Assert.assertTrue(json.getBoolean("writable"));
        JsonArray acls = json.getJsonArray("acls");
        Assert.assertEquals(2, acls.size());
        
        // Create a tag with space (not allowed)
        response = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .put(Entity.form(new Form()
                        .param("name", "Tag 4")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        
        // Create a document
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .put(Entity.form(new Form()
                        .param("title", "My super document 1")
                        .param("tags", tag3Id)
                        .param("language", "eng")), JsonObject.class);
        String document1Id = json.getString("id");
        
        // Create a document
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .put(Entity.form(new Form()
                        .param("title", "My super document 2")
                        .param("tags", tag4Id)
                        .param("language", "eng")), JsonObject.class);
        String document2Id = json.getString("id");

        // Search document by parent tag
        json = target().path("/document/list")
                .queryParam("search", "tag:Tag3")
                .queryParam("asc", "true")
                .queryParam("sort_column", "1")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        Assert.assertEquals(2, json.getJsonArray("documents").size());
        Assert.assertEquals(document1Id, json.getJsonArray("documents").getJsonObject(0).getString("id"));
        Assert.assertEquals(document2Id, json.getJsonArray("documents").getJsonObject(1).getString("id"));

        // Search document by children tag
        json = target().path("/document/list")
                .queryParam("search", "tag:Tag4")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        Assert.assertEquals(1, json.getJsonArray("documents").size());
        Assert.assertEquals(document2Id, json.getJsonArray("documents").getJsonObject(0).getString("id"));

        // Check tags on a document
        json = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        JsonArray tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tag4Id, tags.getJsonObject(0).getString("id"));
        
        // Update tags on a document
        response = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .post(Entity.form(new Form()
                        .param("title", "My super document 2")
                        .param("language", "eng")
                        .param("tags", tag3Id)
                        .param("tags", tag4Id)));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        
        // Check tags on a document
        json = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals(tag3Id, tags.getJsonObject(0).getString("id"));
        Assert.assertEquals(tag4Id, tags.getJsonObject(1).getString("id"));
        
        // Update tags on a document
        response = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .post(Entity.form(new Form()
                        .param("title", "My super document 2")
                        .param("language", "eng")
                        .param("tags", tag4Id)));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        
        // Check tags on a document
        json = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tag4Id, tags.getJsonObject(0).getString("id"));
        
        // Get all tags
        json = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals("Tag4", tags.getJsonObject(1).getString("name"));
        Assert.assertEquals("#00ff00", tags.getJsonObject(1).getString("color"));
        Assert.assertEquals(tag3Id, tags.getJsonObject(1).getString("parent"));
        
        // Update a tag
        json = target().path("/tag/" + tag4Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .post(Entity.form(new Form()
                        .param("name", "UpdatedName")
                        .param("color", "#0000ff")), JsonObject.class);
        Assert.assertEquals(tag4Id, json.getString("id"));
        
        // Get all tags
        json = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals("UpdatedName", tags.getJsonObject(1).getString("name"));
        Assert.assertEquals("#0000ff", tags.getJsonObject(1).getString("color"));
        Assert.assertNull(tags.getJsonObject(1).get("parent"));

        // Update a tag
        json = target().path("/tag/" + tag4Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .post(Entity.form(new Form()
                        .param("name", "UpdatedName")
                        .param("color", "#0000ff")
                        .param("parent", tag3Id)), JsonObject.class);
        Assert.assertEquals(tag4Id, json.getString("id"));

        // Get all tags
        json = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals(tag3Id, tags.getJsonObject(1).getString("parent"));

        // Deletes a tag
        target().path("/tag/" + tag3Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .delete();
        
        // Get all tags
        json = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("UpdatedName", tags.getJsonObject(0).getString("name"));
        Assert.assertNull(tags.getJsonObject(0).get("parent"));

        // Deletes user tag1
        String adminToken = adminToken();
        target().path("/user/tag1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }

    /**
     * Test tag reordering.
     */
    @Test
    public void testTagReorder() {
        clientUtil.createUser("reorder_user");
        String userToken = clientUtil.login("reorder_user");

        // Create 3 tags
        JsonObject jsonA = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .put(Entity.form(new Form()
                        .param("name", "TagAlpha")
                        .param("color", "#111111")), JsonObject.class);
        String idA = jsonA.getString("id");

        JsonObject jsonB = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .put(Entity.form(new Form()
                        .param("name", "TagBeta")
                        .param("color", "#222222")), JsonObject.class);
        String idB = jsonB.getString("id");

        JsonObject jsonC = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .put(Entity.form(new Form()
                        .param("name", "TagGamma")
                        .param("color", "#333333")), JsonObject.class);
        String idC = jsonC.getString("id");

        // Verify initial creation order (Alpha -> Beta -> Gamma)
        JsonObject listJson = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        JsonArray tags = listJson.getJsonArray("tags");
        Assert.assertEquals(idA, tags.getJsonObject(0).getString("id"));
        Assert.assertEquals(idB, tags.getJsonObject(1).getString("id"));
        Assert.assertEquals(idC, tags.getJsonObject(2).getString("id"));

        // Reorder tags: Gamma -> Alpha -> Beta
        Response response = target().path("/tag/reorder").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .post(Entity.form(new Form()
                        .param("tag_ids_csv", idC + "," + idA + "," + idB)));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        // Verify reordered list
        listJson = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        tags = listJson.getJsonArray("tags");
        Assert.assertEquals(idC, tags.getJsonObject(0).getString("id"));
        Assert.assertEquals(idA, tags.getJsonObject(1).getString("id"));
        Assert.assertEquals(idB, tags.getJsonObject(2).getString("id"));

        // Clean up
        target().path("/tag/" + idA).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .delete();
        target().path("/tag/" + idB).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .delete();
        target().path("/tag/" + idC).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .delete();
        String adminToken = adminToken();
        target().path("/user/reorder_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }

    /**
     * Test document reordering within a tag and ghost cleanup.
     */
    @Test
    public void testDocumentReorderInTag() {
        clientUtil.createUser("doc_reorder_user");
        String userToken = clientUtil.login("doc_reorder_user");

        // Create a tag
        JsonObject tagJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .put(Entity.form(new Form()
                        .param("name", "Projects")
                        .param("color", "#abcdef")), JsonObject.class);
        String tagId = tagJson.getString("id");

        // Create 3 documents tagged with 'Projects'
        // Created in order: Zebra, Apple, Mango
        JsonObject doc1Json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .put(Entity.form(new Form()
                        .param("title", "Zebra Project")
                        .param("language", "eng")
                        .param("tags", tagId)), JsonObject.class);
        String doc1Id = doc1Json.getString("id");

        JsonObject doc2Json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .put(Entity.form(new Form()
                        .param("title", "Apple Project")
                        .param("language", "eng")
                        .param("tags", tagId)), JsonObject.class);
        String doc2Id = doc2Json.getString("id");

        JsonObject doc3Json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .put(Entity.form(new Form()
                        .param("title", "Mango Project")
                        .param("language", "eng")
                        .param("tags", tagId)), JsonObject.class);
        String doc3Id = doc3Json.getString("id");

        // Verify initial list (ordered by DOT_ORDER_N ASC, which is creation order 0, 1, 2)
        JsonObject listJson = target().path("/document/list")
                .queryParam("tag_id", tagId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        JsonArray docs = listJson.getJsonArray("documents");
        Assert.assertEquals(3, docs.size());
        Assert.assertEquals(doc1Id, docs.getJsonObject(0).getString("id"));
        Assert.assertEquals(doc2Id, docs.getJsonObject(1).getString("id"));
        Assert.assertEquals(doc3Id, docs.getJsonObject(2).getString("id"));

        // Reorder documents in this tag: Mango -> Apple -> Zebra
        Response response = target().path("/tag/" + tagId + "/document/reorder").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .post(Entity.form(new Form()
                        .param("document_ids_csv", doc3Id + "," + doc2Id + "," + doc1Id)));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        // Verify reordered list: Mango -> Apple -> Zebra
        listJson = target().path("/document/list")
                .queryParam("tag_id", tagId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        docs = listJson.getJsonArray("documents");
        Assert.assertEquals(3, docs.size());
        Assert.assertEquals(doc3Id, docs.getJsonObject(0).getString("id"));
        Assert.assertEquals(doc2Id, docs.getJsonObject(1).getString("id"));
        Assert.assertEquals(doc1Id, docs.getJsonObject(2).getString("id"));

        // Test ghost cleanup: delete Mango document
        target().path("/document/" + doc3Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .delete();

        // Listing documents in tag now only returns 2 active documents (Apple, Zebra)
        listJson = target().path("/document/list")
                .queryParam("tag_id", tagId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        docs = listJson.getJsonArray("documents");
        Assert.assertEquals(2, docs.size());
        Assert.assertEquals(doc2Id, docs.getJsonObject(0).getString("id"));
        Assert.assertEquals(doc1Id, docs.getJsonObject(1).getString("id"));

        // Clean up remaining documents, tag, and user
        target().path("/document/" + doc1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .delete();
        target().path("/document/" + doc2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .delete();
        target().path("/tag/" + tagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .delete();
        String adminToken = adminToken();
        target().path("/user/doc_reorder_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }
}
