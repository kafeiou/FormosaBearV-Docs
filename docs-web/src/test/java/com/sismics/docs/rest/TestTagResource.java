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

        // Cannot delete tag3 because it has a child (Tag4) -> Guardrail TagHasChildren
        Response failDelete = target().path("/tag/" + tag3Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(failDelete.getStatus()));

        // Both tags still exist
        json = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(2, tags.size());

        // Delete child tag4Id first
        Response successDeleteChild = target().path("/tag/" + tag4Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(successDeleteChild.getStatus()));

        // Now parent tag3Id can be deleted
        Response successDeleteParent = target().path("/tag/" + tag3Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(successDeleteParent.getStatus()));

        // Verify all tags deleted
        json = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tag1Token)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(0, tags.size());

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

    /**
     * Test hierarchical tag permissions, inheritance break, union access, and container tags.
     */
    @Test
    public void testHierarchicalTagPermissions() {
        // Create test users
        clientUtil.createUser("h_admin");
        clientUtil.createUser("h_reader");
        clientUtil.createUser("h_writer");
        clientUtil.createUser("h_unauth");
        String adminUserToken = clientUtil.login("h_admin");
        String readerToken = clientUtil.login("h_reader");
        String writerToken = clientUtil.login("h_writer");
        String unauthToken = clientUtil.login("h_unauth");

        // 1. h_admin creates RootTag
        JsonObject rootJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("name", "HRoot")
                        .param("color", "#111111")), JsonObject.class);
        String rootTagId = rootJson.getString("id");

        // Grant READ on RootTag to h_reader
        target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("source", rootTagId)
                        .param("perm", "READ")
                        .param("target", "h_reader")
                        .param("type", "USER")), JsonObject.class);

        // Grant WRITE on RootTag to h_writer
        target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("source", rootTagId)
                        .param("perm", "WRITE")
                        .param("target", "h_writer")
                        .param("type", "USER")), JsonObject.class);

        // 2. h_admin creates ChildTag under RootTag (inherit_acl = true)
        JsonObject childJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("name", "HChild")
                        .param("color", "#222222")
                        .param("parent", rootTagId)
                        .param("inherit_acl", "true")), JsonObject.class);
        String childTagId = childJson.getString("id");

        // h_admin creates DocInChild tagged with ChildTag
        JsonObject doc1Json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("title", "DocInChild")
                        .param("language", "eng")
                        .param("tags", childTagId)), JsonObject.class);
        String docInChildId = doc1Json.getString("id");

        // Verify ChildTag inherits READ: h_reader can read ChildTag and DocInChild
        JsonObject childGet = target().path("/tag/" + childTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get(JsonObject.class);
        Assert.assertEquals("HChild", childGet.getString("name"));
        Assert.assertTrue(childGet.getBoolean("inherit_acl"));
        Assert.assertFalse(childGet.getBoolean("is_container"));
        Assert.assertEquals(rootTagId, childGet.getString("parent"));

        JsonObject listReader = target().path("/document/list")
                .queryParam("tag_id", childTagId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, listReader.getJsonArray("documents").size());
        Assert.assertEquals(docInChildId, listReader.getJsonArray("documents").getJsonObject(0).getString("id"));

        // Verify h_reader cannot WRITE to ChildTag (cannot update it)
        Response failUpdate = target().path("/tag/" + childTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .post(Entity.form(new Form()
                        .param("name", "HChild")
                        .param("color", "#333333")
                        .param("parent", rootTagId)));
        Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(failUpdate.getStatus()));

        // Verify ChildTag inherits WRITE: h_writer CAN update ChildTag
        Response successUpdate = target().path("/tag/" + childTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, writerToken)
                .post(Entity.form(new Form()
                        .param("name", "HChild")
                        .param("color", "#333333")
                        .param("parent", rootTagId)));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(successUpdate.getStatus()));

        // Verify h_unauth cannot see ChildTag or DocInChild
        Response failGetTag = target().path("/tag/" + childTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, unauthToken)
                .get();
        Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(failGetTag.getStatus()));

        // 3. Test Broken Inheritance (inherit_acl = false)
        JsonObject secretJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("name", "HSecret")
                        .param("color", "#444444")
                        .param("parent", rootTagId)
                        .param("inherit_acl", "false")), JsonObject.class);
        String secretTagId = secretJson.getString("id");

        JsonObject docSecretJson = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("title", "DocInSecret")
                        .param("language", "eng")
                        .param("tags", secretTagId)), JsonObject.class);
        String docSecretId = docSecretJson.getString("id");

        // Verify h_reader & h_writer CANNOT access SecretTag or DocInSecret (inheritance broken!)
        Response failReaderSecret = target().path("/tag/" + secretTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get();
        Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(failReaderSecret.getStatus()));

        Response failWriterSecret = target().path("/tag/" + secretTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, writerToken)
                .get();
        Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(failWriterSecret.getStatus()));

        JsonObject listReaderSecretDocs = target().path("/document/list")
                .queryParam("tag_id", secretTagId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get(JsonObject.class);
        Assert.assertEquals(0, listReaderSecretDocs.getJsonArray("documents").size());

        // Grant READ on SecretTag directly to h_unauth
        target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("source", secretTagId)
                        .param("perm", "READ")
                        .param("target", "h_unauth")
                        .param("type", "USER")), JsonObject.class);

        // Now h_unauth CAN see SecretTag and DocInSecret
        JsonObject secretGetUnauth = target().path("/tag/" + secretTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, unauthToken)
                .get(JsonObject.class);
        Assert.assertEquals("HSecret", secretGetUnauth.getString("name"));
        Assert.assertFalse(secretGetUnauth.getBoolean("inherit_acl"));

        JsonObject listUnauthDocs = target().path("/document/list")
                .queryParam("tag_id", secretTagId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, unauthToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, listUnauthDocs.getJsonArray("documents").size());
        Assert.assertEquals(docSecretId, listUnauthDocs.getJsonArray("documents").getJsonObject(0).getString("id"));

        // 4. Test Multi-tag Union Access: DocMulti tagged with both SecretTag and ChildTag
        JsonObject docMultiJson = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("title", "DocMulti")
                        .param("language", "eng")
                        .param("tags", secretTagId)
                        .param("tags", childTagId)), JsonObject.class);
        String docMultiId = docMultiJson.getString("id");

        // h_reader can access DocMulti (via ChildTag) even though h_reader has no access to SecretTag
        JsonObject docMultiReader = target().path("/document/" + docMultiId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get(JsonObject.class);
        Assert.assertEquals(docMultiId, docMultiReader.getString("id"));

        // h_unauth can access DocMulti (via SecretTag) even though h_unauth has no access to ChildTag
        JsonObject docMultiUnauth = target().path("/document/" + docMultiId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, unauthToken)
                .get(JsonObject.class);
        Assert.assertEquals(docMultiId, docMultiUnauth.getString("id"));

        // 5. Test Path Container Tag (is_container: true)
        // h_unauth only has access to SecretTag, parent RootTag must be returned as is_container: true
        JsonObject unauthTagList = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, unauthToken)
                .get(JsonObject.class);
        JsonArray unauthTags = unauthTagList.getJsonArray("tags");
        Assert.assertEquals(2, unauthTags.size());
        boolean foundRootContainer = false;
        boolean foundSecretTag = false;
        for (int i = 0; i < unauthTags.size(); i++) {
            JsonObject t = unauthTags.getJsonObject(i);
            if (t.getString("id").equals(rootTagId)) {
                foundRootContainer = true;
                Assert.assertTrue(t.getBoolean("is_container"));
                Assert.assertEquals(0, t.getInt("document_count"));
            } else if (t.getString("id").equals(secretTagId)) {
                foundSecretTag = true;
                Assert.assertFalse(t.getBoolean("is_container"));
                Assert.assertEquals(rootTagId, t.getString("parent"));
            }
        }
        Assert.assertTrue(foundRootContainer);
        Assert.assertTrue(foundSecretTag);

        // 6. Test State Transition: breaking & restoring inheritance on SubTag
        JsonObject subJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .put(Entity.form(new Form()
                        .param("name", "HSub")
                        .param("color", "#555555")
                        .param("parent", rootTagId)
                        .param("inherit_acl", "true")), JsonObject.class);
        String subTagId = subJson.getString("id");

        // Initially inherits: inherited_acls has RootTag's ACLs
        JsonObject subGet1 = target().path("/tag/" + subTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .get(JsonObject.class);
        Assert.assertTrue(subGet1.getJsonArray("inherited_acls").size() >= 2);

        // Break inheritance: POST inherit_acl = false -> parent ACLs copied to direct ACLs
        target().path("/tag/" + subTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .post(Entity.form(new Form()
                        .param("name", "HSub")
                        .param("color", "#555555")
                        .param("parent", rootTagId)
                        .param("inherit_acl", "false")));

        JsonObject subGet2 = target().path("/tag/" + subTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .get(JsonObject.class);
        Assert.assertFalse(subGet2.getBoolean("inherit_acl"));
        Assert.assertEquals(0, subGet2.getJsonArray("inherited_acls").size());
        Assert.assertTrue(subGet2.getJsonArray("acls").size() >= 2);

        // Restore inheritance: POST inherit_acl = true -> direct ACLs cleared, inherited_acls restored
        target().path("/tag/" + subTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .post(Entity.form(new Form()
                        .param("name", "HSub")
                        .param("color", "#555555")
                        .param("parent", rootTagId)
                        .param("inherit_acl", "true")));

        JsonObject subGet3 = target().path("/tag/" + subTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken)
                .get(JsonObject.class);
        Assert.assertTrue(subGet3.getBoolean("inherit_acl"));
        Assert.assertEquals(0, subGet3.getJsonArray("acls").size());
        Assert.assertTrue(subGet3.getJsonArray("inherited_acls").size() >= 2);

        // 5. Test Tag Report Export endpoint (/tag/export) for JSON, XLSX, HTML, PDF
        // Missing tag_id -> 400 Bad Request
        Response missingTagResp = target().path("/tag/export")
                .queryParam("format", "json")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(missingTagResp.getStatus()));

        // Invalid format -> 400 Bad Request
        Response invalidFmtResp = target().path("/tag/export")
                .queryParam("tag_id", rootTagId)
                .queryParam("format", "csv")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(invalidFmtResp.getStatus()));

        // Unauthorized user requesting a tag they have no permission on -> 403 Forbidden
        Response unauthExportResp = target().path("/tag/export")
                .queryParam("tag_id", childTagId)
                .queryParam("format", "json")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, unauthToken)
                .get();
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(unauthExportResp.getStatus()));

        // Authorized reader exports RootTag with include_children=true in JSON format
        // Note: SecretTag (inherit_acl=false) must NOT be included for h_reader, while RootTag, ChildTag, SubTag are included
        Response jsonExportResp = target().path("/tag/export")
                .queryParam("tag_id", rootTagId)
                .queryParam("format", "json")
                .queryParam("include_children", "true")
                .queryParam("include_date", "false")
                .queryParam("include_description", "false")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(jsonExportResp.getStatus()));
        Assert.assertTrue(jsonExportResp.getHeaderString("Content-Disposition").contains(".zip"));
        byte[] jsonZipBytes = jsonExportResp.readEntity(byte[].class);
        JsonObject exportedJson = null;
        String readmeContent = null;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(jsonZipBytes), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (entry.getName().endsWith(".json")) {
                    exportedJson = jakarta.json.Json.createReader(new java.io.StringReader(content)).readObject();
                } else if ("README.md".equals(entry.getName())) {
                    readmeContent = content;
                }
                zis.closeEntry();
            }
        } catch (java.io.IOException e) {
            Assert.fail("Failed to read JSON ZIP export: " + e.getMessage());
        }
        Assert.assertNotNull(exportedJson);
        Assert.assertNotNull(readmeContent);
        Assert.assertTrue(readmeContent.contains("# Tag Report JSON Export Schema"));
        Assert.assertEquals(3, exportedJson.getJsonObject("statistics").getInt("total_tags"));

        // XLSX export
        Response xlsxExportResp = target().path("/tag/export")
                .queryParam("tag_id", rootTagId)
                .queryParam("format", "xlsx")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(xlsxExportResp.getStatus()));
        Assert.assertTrue(xlsxExportResp.getHeaderString("Content-Disposition").contains(".xlsx"));
        byte[] xlsxBytes = xlsxExportResp.readEntity(byte[].class);
        Assert.assertTrue(xlsxBytes.length > 500);

        // HTML export
        Response htmlExportResp = target().path("/tag/export")
                .queryParam("tag_id", rootTagId)
                .queryParam("format", "html")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(htmlExportResp.getStatus()));
        Assert.assertTrue(htmlExportResp.getHeaderString("Content-Disposition").contains(".html"));
        String htmlBody = htmlExportResp.readEntity(String.class);
        Assert.assertTrue(htmlBody.contains("HRoot"));
        Assert.assertTrue(htmlBody.contains("HChild"));
        Assert.assertFalse(htmlBody.contains("HSecret"));

        // PDF export
        Response pdfExportResp = target().path("/tag/export")
                .queryParam("tag_id", rootTagId)
                .queryParam("format", "pdf")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, readerToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(pdfExportResp.getStatus()));
        Assert.assertTrue(pdfExportResp.getHeaderString("Content-Disposition").contains(".pdf"));
        byte[] pdfBytes = pdfExportResp.readEntity(byte[].class);
        Assert.assertTrue(pdfBytes.length > 500);

        // Cleanup
        target().path("/document/" + docMultiId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken).delete();
        target().path("/document/" + docSecretId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken).delete();
        target().path("/document/" + docInChildId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken).delete();
        target().path("/tag/" + subTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken).delete();
        target().path("/tag/" + secretTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken).delete();
        target().path("/tag/" + childTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken).delete();
        target().path("/tag/" + rootTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminUserToken).delete();
        String sysAdminToken = adminToken();
        target().path("/user/h_admin").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, sysAdminToken).delete();
        target().path("/user/h_reader").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, sysAdminToken).delete();
        target().path("/user/h_writer").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, sysAdminToken).delete();
        target().path("/user/h_unauth").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, sysAdminToken).delete();
    }
}
