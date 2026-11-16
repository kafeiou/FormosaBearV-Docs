package com.sismics.docs.rest;

import com.sismics.util.filter.TokenBasedSecurityFilter;
import org.junit.Assert;
import org.junit.Test;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;


/**
 * Test the group resource.
 * 
 * @author bgamard
 */
public class TestGroupResource extends BaseJerseyTest {
    /**
     * Test the group resource.
     */
    @Test
    public void testGroupResource() {
        // Login admin
        String adminToken = adminToken();
        
        // Create group hierarchy
        clientUtil.createGroup("g1");
        clientUtil.createGroup("g11", "g1");
        clientUtil.createGroup("g12", "g1");
        clientUtil.createGroup("g111", "g11");
        clientUtil.createGroup("g112", "g11");
        
        // Login group1
        clientUtil.createUser("group1", "g112", "g12");
        String group1Token = clientUtil.login("group1");
        
        // Login admin2
        clientUtil.createUser("admin2", "administrators");
        String admin2Token = clientUtil.login("admin2");
        
        // Create trashme
        clientUtil.createUser("trashme");
        
        // Delete trashme with admin2
        target().path("/user/trashme").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, admin2Token)
                .delete(JsonObject.class);
        
        // Get all groups
        JsonObject json = target().path("/group")
                .queryParam("sort_column", "1")
                .queryParam("asc", "true")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray groups = json.getJsonArray("groups");
        Assert.assertEquals(7, groups.size());
        JsonObject groupG11 = groups.getJsonObject(2);
        Assert.assertEquals("g11", groupG11.getString("name"));
        Assert.assertEquals("g1", groupG11.getString("parent"));
        
        // Check admin groups (all computed groups)
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        groups = json.getJsonArray("groups");
        Assert.assertEquals(2, groups.size());
        List<String> adminGroupList = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            adminGroupList.add(groups.getString(i));
        }
        Assert.assertTrue(adminGroupList.contains("administrators"));
        Assert.assertTrue(adminGroupList.contains("tag_editor"));
        
        // Check group1 groups (all computed groups)
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, group1Token)
                .get(JsonObject.class);
        groups = json.getJsonArray("groups");
        List<String> groupList = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            groupList.add(groups.getString(i));
        }
        Assert.assertEquals(4, groups.size());
        Assert.assertTrue(groupList.contains("g1"));
        Assert.assertTrue(groupList.contains("g12"));
        Assert.assertTrue(groupList.contains("g11"));
        Assert.assertTrue(groupList.contains("g112"));
        
        // Check group1 groups with admin (only direct groups)
        json = target().path("/user/group1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        groups = json.getJsonArray("groups");
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals("g112", groups.getString(0));
        Assert.assertEquals("g12", groups.getString(1));
        
        // List all users in group1
        json = target().path("/user/list")
                .queryParam("group", "g112")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray users = json.getJsonArray("users");
        Assert.assertEquals(1, users.size());
        
        // Add group1 to g112 (again)
        target().path("/group/g112").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("username", "group1")), JsonObject.class);
        
        // Check group1 groups (all computed groups)
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, group1Token)
                .get(JsonObject.class);
        groups = json.getJsonArray("groups");
        Assert.assertEquals(4, groups.size());
        
        // Update group g12
        target().path("/group/g12").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("name", "g12new")
                        .param("parent", "g11")), JsonObject.class);
        
        // Check group1 groups with admin (only direct groups)
        json = target().path("/user/group1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        groups = json.getJsonArray("groups");
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals("g112", groups.getString(0));
        Assert.assertEquals("g12new", groups.getString(1));
        
        // Get group g12new
        json = target().path("/group/g12new").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("g12new", json.getString("name"));
        Assert.assertEquals("g11", json.getString("parent"));
        JsonArray members = json.getJsonArray("members");
        Assert.assertEquals(1, members.size());
        Assert.assertEquals("group1", members.getString(0));
        
        // Remove group1 from g12new
        target().path("/group/g12new/group1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        
        // Check group1 groups (all computed groups)
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, group1Token)
                .get(JsonObject.class);
        groups = json.getJsonArray("groups");
        groupList = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            groupList.add(groups.getString(i));
        }
        Assert.assertEquals(3, groups.size());
        Assert.assertTrue(groupList.contains("g1"));
        Assert.assertTrue(groupList.contains("g11"));
        Assert.assertTrue(groupList.contains("g112"));
        
        // Delete group g1
        target().path("/group/g1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);

        // Delete group administrators
        Response response = target().path("/group/administrators").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ForbiddenError", json.getString("type"));
        Assert.assertEquals("The administrators group cannot be deleted", json.getString("message"));
        
        // Check group1 groups (all computed groups)
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, group1Token)
                .get(JsonObject.class);
        groups = json.getJsonArray("groups");
        groupList = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            groupList.add(groups.getString(i));
        }
        Assert.assertEquals(2, groups.size());
        Assert.assertTrue(groupList.contains("g11"));
        Assert.assertTrue(groupList.contains("g112"));

        // Delete all remaining groups and users
        target().path("/group/g11").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        target().path("/group/g12new").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        target().path("/group/g111").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        target().path("/group/g112").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        target().path("/user/group1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        target().path("/user/admin2").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }

    @Test
    public void testGroupGuardrailsAndVisibility() {
        String adminToken = adminToken();
        clientUtil.createGroup("team_alpha");
        clientUtil.createGroup("team_beta");
        clientUtil.createUser("member_a", "team_alpha");
        String memberAToken = clientUtil.login("member_a");

        // 1. Admin cannot remove themselves from administrators or tag_editor
        Response removeSelfResp = target().path("/group/administrators/admin").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(removeSelfResp.getStatus()));
        Assert.assertEquals("CannotRemoveSelfFromGroup", removeSelfResp.readEntity(JsonObject.class).getString("type"));

        // 2. Built-in groups (tag_editor, administrators) cannot be renamed
        Response renameTagEditorResp = target().path("/group/tag_editor").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("name", "tag_editor_renamed")));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(renameTagEditorResp.getStatus()));
        Assert.assertEquals("ProtectedGroupName", renameTagEditorResp.readEntity(JsonObject.class).getString("type"));

        // 3. Default GROUP_VIEW_ALL=false: regular user only sees own groups and gets 403 on non-member groups
        JsonObject myGroupsJson = target().path("/group").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, memberAToken)
                .get(JsonObject.class);
        JsonArray myGroups = myGroupsJson.getJsonArray("groups");
        Assert.assertEquals(1, myGroups.size());
        Assert.assertEquals("team_alpha", myGroups.getJsonObject(0).getString("name"));

        Response viewBetaDenied = target().path("/group/team_beta").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, memberAToken)
                .get();
        Assert.assertEquals(Response.Status.FORBIDDEN, Response.Status.fromStatusCode(viewBetaDenied.getStatus()));

        // 4. Enable GROUP_VIEW_ALL=true: regular user can now view team_beta
        Response enableViewAll = target().path("/app/group_view_all").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("enabled", "true")));
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(enableViewAll.getStatus()));

        JsonObject betaJson = target().path("/group/team_beta").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, memberAToken)
                .get(JsonObject.class);
        Assert.assertEquals("team_beta", betaJson.getString("name"));

        // Restore default GROUP_VIEW_ALL=false
        target().path("/app/group_view_all").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("enabled", "false")));

        // 5. Guest account is always blocked from viewing groups
        target().path("/app/guest_login").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("enabled", "true")));
        String guestToken = clientUtil.login("guest", "", false);
        Response guestGroupList = target().path("/group").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .get();
        Assert.assertEquals(Response.Status.FORBIDDEN, Response.Status.fromStatusCode(guestGroupList.getStatus()));
        Response guestGroupGet = target().path("/group/team_alpha").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .get();
        Assert.assertEquals(Response.Status.FORBIDDEN, Response.Status.fromStatusCode(guestGroupGet.getStatus()));
        target().path("/app/guest_login").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("enabled", "false")));

        // 6. Non-admin user cannot remove own or own-group ACL on a tag
        JsonObject tagJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form().param("name", "SharedAlphaTag").param("color", "#3a87ad")), JsonObject.class);
        String tagId = tagJson.getString("id");
        target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form().param("source", tagId).param("perm", "WRITE").param("target", "team_alpha").param("type", "GROUP")));
        Response removeOwnGroupAcl = target().path("/acl/" + tagId + "/WRITE/" + myGroups.getJsonObject(0).getString("id")).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, memberAToken)
                .delete();
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(removeOwnGroupAcl.getStatus()));
        Assert.assertEquals("CannotRemoveOwnAcl", removeOwnGroupAcl.readEntity(JsonObject.class).getString("type"));

        // 7. User only in tag_editor group must NOT inherit ADMIN privileges or edit administrators
        clientUtil.createUser("tag_editor_only", "tag_editor");
        String tagEditorToken = clientUtil.login("tag_editor_only");
        JsonObject tagEditorUser = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tagEditorToken)
                .get(JsonObject.class);
        JsonArray baseFuncs = tagEditorUser.getJsonArray("base_functions");
        for (int i = 0; i < baseFuncs.size(); i++) {
            Assert.assertNotEquals("ADMIN", baseFuncs.getString(i));
        }
        Response editAdminsDenied = target().path("/group/administrators").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, tagEditorToken)
                .put(Entity.form(new Form().param("username", "tag_editor_only")));
        Assert.assertEquals(Response.Status.FORBIDDEN, Response.Status.fromStatusCode(editAdminsDenied.getStatus()));

        // Cleanup
        target().path("/tag/" + tagId).request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken).delete();
        target().path("/group/team_alpha").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken).delete();
        target().path("/group/team_beta").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken).delete();
        target().path("/user/member_a").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken).delete();
        target().path("/user/tag_editor_only").request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken).delete();
    }
}
