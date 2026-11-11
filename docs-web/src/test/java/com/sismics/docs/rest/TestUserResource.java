package com.sismics.docs.rest;

import com.sismics.util.filter.TokenBasedSecurityFilter;
import com.sismics.util.totp.GoogleAuthenticator;
import org.junit.Assert;
import org.junit.Test;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exhaustive test of the user resource.
 * 
 * @author jtremeaux
 */
public class TestUserResource extends BaseJerseyTest {
    /**
     * Test the user resource.
     */
    @Test
    public void testUserResource() {
        // Check anonymous user information
        JsonObject json = target().path("/user").request()
                .acceptLanguage(Locale.US)
                .get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("is_default_password"));
        
        // Create alice user
        clientUtil.createUser("alice");

        // Login admin
        String adminToken = adminToken();
        
        // List all users
        json = target().path("/user/list")
                .queryParam("sort_column", 2)
                .queryParam("asc", false)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray users = json.getJsonArray("users");
        Assert.assertTrue(users.size() > 0);
        JsonObject user = users.getJsonObject(0);
        Assert.assertNotNull(user.getString("id"));
        Assert.assertNotNull(user.getString("username"));
        Assert.assertNotNull(user.getString("email"));
        Assert.assertNotNull(user.getJsonNumber("storage_quota"));
        Assert.assertNotNull(user.getJsonNumber("storage_current"));
        Assert.assertNotNull(user.getJsonNumber("create_date"));
        Assert.assertFalse(user.getBoolean("totp_enabled"));
        Assert.assertFalse(user.getBoolean("disabled"));

        // Create a user KO (login length validation)
        Response response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("username", "   bb  ")
                        .param("email", "bob@docs.com")
                        .param("password", "12345678")
                        .param("storage_quota", "10")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ValidationError", json.getString("type"));
        Assert.assertTrue(json.getString("message"), json.getString("message").contains("more than 3"));

        // Create a user KO (login format validation)
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("username", "bob/")
                        .param("email", "bob@docs.com")
                        .param("password", "12345678")
                        .param("storage_quota", "10")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ValidationError", json.getString("type"));
        Assert.assertTrue(json.getString("message"), json.getString("message").contains("alphanumeric"));
        
        // Create a user KO (invalid quota)
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("username", "bob")
                        .param("email", "bob@docs.com")
                        .param("password", "12345678")
                        .param("storage_quota", "nope")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ValidationError", json.getString("type"));
        Assert.assertTrue(json.getString("message"), json.getString("message").contains("number"));

        // Create a user KO (email format validation)
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("username", "bob")
                        .param("email", "bobdocs.com")
                        .param("password", "12345678")
                        .param("storage_quota", "10")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ValidationError", json.getString("type"));
        Assert.assertTrue(json.getString("message"), json.getString("message").contains("must be an email"));

        // Create a user bob OK
        Form form = new Form()
                .param("username", " bob ")
                .param("email", " bob@docs.com ")
                .param("password", " 12345678 ")
                .param("storage_quota", "10");
        target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(form), JsonObject.class);

        // Create a user bob KO : duplicate username
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(form));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("AlreadyExistingUsername", json.getString("type"));

        // Login alice with extra whitespaces
        response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", " alice ")
                        .param("password", " 12345678 ")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        String aliceAuthToken = clientUtil.getAuthenticationCookie(response);

        // Login user bob twice
        String bobToken = clientUtil.login("bob");
        String bobToken2 = clientUtil.login("bob");

        // List sessions
        response = target().path("/user/session").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertTrue(json.getJsonArray("sessions").size() > 0);
        JsonObject session = json.getJsonArray("sessions").getJsonObject(0);
        Assert.assertEquals("127.0.0.1", session.getString("ip"));
        Assert.assertTrue(session.getString("user_agent").startsWith("Jersey"));
        
        // Delete all sessions
        response = target().path("/user/session").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        // Check bob user information with token 2 (just deleted)
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken2)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertTrue(json.getBoolean("anonymous"));
        
        // Check alice user information
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceAuthToken)
                .get(JsonObject.class);
        Assert.assertEquals("alice@docs.com", json.getString("email"));
        Assert.assertFalse(json.getBoolean("is_default_password"));
        Assert.assertEquals(0L, json.getJsonNumber("storage_current").longValue());
        Assert.assertEquals(1000000L, json.getJsonNumber("storage_quota").longValue());
        
        // Check bob user information
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("onboarding"));
        Assert.assertEquals("bob@docs.com", json.getString("email"));

        // Pass onboarding
        target().path("/user/onboarded").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .post(Entity.form(new Form()), JsonObject.class);

        // Check bob user information
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .get(JsonObject.class);
        Assert.assertFalse(json.getBoolean("onboarding"));

        // Test login KO (user not found)
        response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "intruder")
                        .param("password", "12345678")));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(response.getStatus()));

        // Test login KO (wrong password)
        response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "alice")
                        .param("password", "error")));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(response.getStatus()));

        // User alice updates her information + changes her email
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceAuthToken)
                .post(Entity.form(new Form()
                        .param("email", " alice2@docs.com ")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Check the update
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceAuthToken)
                .get(JsonObject.class);
        Assert.assertEquals("alice2@docs.com", json.getString("email"));
        
        // Delete user alice
        target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceAuthToken)
                .delete();
        
        // Check the deletion
        response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "alice")
                        .param("password", "12345678")));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(response.getStatus()));

        // Delete user bob
        target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .delete();
    }

    /**
     * Test the user resource admin functions.
     */
    @Test
    public void testUserResourceAdmin() {
        // Create admin_user1 user
        clientUtil.createUser("admin_user1");

        // Login admin
        String adminToken = adminToken();

        // Check admin information
        JsonObject json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("is_default_password"));
        Assert.assertEquals(0L, json.getJsonNumber("storage_current").longValue());
        Assert.assertEquals(10000000000L, json.getJsonNumber("storage_quota").longValue());

        // User admin updates his information
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("email", "newadminemail@docs.com")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));

        // Check admin information update
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("newadminemail@docs.com", json.getString("email"));

        // User admin update admin_user1 information
        json = target().path("/user/admin_user1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("email", " alice2@docs.com ")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // User admin deletes himself: forbidden
        Response response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ForbiddenError", json.getString("type"));

        // User admin disable admin_user1
        json = target().path("/user/admin_user1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("disabled", "true")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));

        // User admin_user1 tries to authenticate
        response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "admin_user1")
                        .param("password", "12345678")
                        .param("remember", "false")));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // User admin enable admin_user1
        json = target().path("/user/admin_user1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("disabled", "false")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));

        // User admin_user1 tries to authenticate
        response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "admin_user1")
                        .param("password", "12345678")
                        .param("remember", "false")));
        Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

        // User admin deletes user admin_user1
        json = target().path("/user/admin_user1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // User admin deletes user admin_user1 : KO (user doesn't exist)
        response = target().path("/user/admin_user1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("UserNotFound", json.getString("type"));
    }
    
    @Test
    public void testTotp() {
        // Login admin
        String adminToken = adminToken();

        // Create totp1 user
        clientUtil.createUser("totp1");
        String totp1Token = clientUtil.login("totp1");
        
        // Check TOTP enablement
        JsonObject json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, totp1Token)
                .get(JsonObject.class);
        Assert.assertFalse(json.getBoolean("totp_enabled"));
        
        // Enable TOTP for totp1
        json = target().path("/user/enable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, totp1Token)
                .post(Entity.form(new Form()), JsonObject.class);
        String secret = json.getString("secret");
        Assert.assertNotNull(secret);
        
        // Try to login with totp1 without a validation code
        Response response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "totp1")
                        .param("password", "12345678")
                        .param("remember", "false")));
        Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ValidationCodeRequired", json.getString("type"));
        
        // Generate a OTP
        GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
        int validationCode = googleAuthenticator.calculateCode(secret, new Date().getTime() / 30000);
        
        // Login with totp1 with a validation code
        target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "totp1")
                        .param("password", "12345678")
                        .param("code", Integer.toString(validationCode))
                        .param("remember", "false")), JsonObject.class);
        
        // Check TOTP enablement
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, totp1Token)
                .get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("totp_enabled"));

        // Generate a OTP
        validationCode = googleAuthenticator.calculateCode(secret, new Date().getTime() / 30000);

        // Test a validation code
        target().path("/user/test_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, totp1Token)
                .post(Entity.form(new Form()
                        .param("code", Integer.toString(validationCode))), JsonObject.class);

        // Disable TOTP for totp1
        target().path("/user/disable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, totp1Token)
                .post(Entity.form(new Form()
                        .param("password", "12345678")), JsonObject.class);

        // Enable TOTP for totp1
        target().path("/user/enable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, totp1Token)
                .post(Entity.form(new Form()), JsonObject.class);

        // Disable TOTP for totp1 with admin
        target().path("/user/totp1/disable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()), JsonObject.class);

        // Login with totp1 without a validation code
        target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "totp1")
                        .param("password", "12345678")
                        .param("remember", "false")), JsonObject.class);
        
        // Check TOTP enablement
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, totp1Token)
                .get(JsonObject.class);
        Assert.assertFalse(json.getBoolean("totp_enabled"));

        // Delete totp1
        response = target().path("/user/totp1").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));
    }

    @Test
    public void testResetPassword() throws Exception {
        // Login admin
        String adminToken = adminToken();

        // Change SMTP configuration to target Wiser
        target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("hostname", "localhost")
                        .param("port", "2500")
                        .param("from", "contact@sismicsdocs.com")
                ), JsonObject.class);

        // Create absent_minded who lost his password
        clientUtil.createUser("absent_minded");

        // User no_such_user try to recovery its password: silently do nothing to avoid leaking users
        JsonObject json = target().path("/user/password_lost").request()
                .post(Entity.form(new Form()
                        .param("username", "no_such_user")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));

        // User absent_minded try to recovery its password: OK
        json = target().path("/user/password_lost").request()
                .post(Entity.form(new Form()
                        .param("username", "absent_minded")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        String emailBody = popEmail();
        Assert.assertNotNull("No email to consume", emailBody);
        Assert.assertTrue(emailBody.contains("Please reset your password"));
        Pattern keyPattern = Pattern.compile("/passwordreset/(.+?)\"");
        Matcher keyMatcher = keyPattern.matcher(emailBody);
        Assert.assertTrue("Token not found", keyMatcher.find());
        String key = keyMatcher.group(1).replaceAll("=", "");

        // User absent_minded resets its password: invalid key
        Response response = target().path("/user/password_reset").request()
                .post(Entity.form(new Form()
                        .param("key", "no_such_key")
                        .param("password", "87654321")));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("KeyNotFound", json.getString("type"));

        // User absent_minded resets its password: password invalid
        response = target().path("/user/password_reset").request()
                .post(Entity.form(new Form()
                        .param("key", key)
                        .param("password", " 1 ")));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("ValidationError", json.getString("type"));
        Assert.assertTrue(json.getString("message"), json.getString("message").contains("password"));

        // User absent_minded resets its password: OK
        json = target().path("/user/password_reset").request()
                .post(Entity.form(new Form()
                        .param("key", key)
                        .param("password", "87654321")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));

        // User absent_minded resets its password: expired key
        response = target().path("/user/password_reset").request()
                .post(Entity.form(new Form()
                        .param("key", key)
                        .param("password", "87654321")));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        json = response.readEntity(JsonObject.class);
        Assert.assertEquals("KeyNotFound", json.getString("type"));

        // Delete absent_minded
        response = target().path("/user/absent_minded").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));
    }

    /**
     * Test LDAP auth_source protection and hybrid auth isolation.
     */
    @Test
    public void testLdapAuthSourceSecurity() {
        String adminToken = adminToken();

        // 1. Create a normal user ldap_user
        clientUtil.createUser("ldap_user");
        String userToken = clientUtil.login("ldap_user", "12345678", false);

        // Check GET /user info
        JsonObject userInfo = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get(JsonObject.class);
        Assert.assertEquals("INTERNAL", userInfo.getString("auth_source"));
        Assert.assertFalse(userInfo.getBoolean("is_ldap"));

        // 2. Admin sets auth_source = LDAP for ldap_user
        Response response = target().path("/user/ldap_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("auth_source", "LDAP")));
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));

        // Check GET /user/ldap_user view
        JsonObject view = target().path("/user/ldap_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("LDAP", view.getString("auth_source"));
        Assert.assertTrue(view.getBoolean("is_ldap"));

        // 3. LDAP user cannot change password via POST /user (self-service)
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .post(Entity.form(new Form()
                        .param("password", "newpassword123")));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        JsonObject errJson = response.readEntity(JsonObject.class);
        Assert.assertEquals("LdapUserPasswordChangeForbidden", errJson.getString("type"));

        // 4. Admin cannot change password for LDAP user without converting to INTERNAL
        response = target().path("/user/ldap_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("password", "newpassword123")));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        errJson = response.readEntity(JsonObject.class);
        Assert.assertEquals("LdapUserPasswordChangeForbidden", errJson.getString("type"));

        // 5. LDAP user requesting password recovery is forbidden
        response = target().path("/user/password_lost").request()
                .post(Entity.form(new Form()
                        .param("username", "ldap_user")));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        errJson = response.readEntity(JsonObject.class);
        Assert.assertEquals("LdapUserPasswordResetForbidden", errJson.getString("type"));

        // 6. InternalAuthenticationHandler & REST login blocks LDAP user from local login
        response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "ldap_user")
                        .param("password", "12345678")
                        .param("remember", "false")));
        Assert.assertEquals(Response.Status.FORBIDDEN, Response.Status.fromStatusCode(response.getStatus()));

        com.sismics.docs.core.util.TransactionUtil.handle(() -> {
            com.sismics.docs.core.util.authentication.InternalAuthenticationHandler internalHandler =
                    new com.sismics.docs.core.util.authentication.InternalAuthenticationHandler();
            com.sismics.docs.core.model.jpa.User authResult = internalHandler.authenticate("ldap_user", "12345678");
            Assert.assertNull("LDAP user must not authenticate with internal password", authResult);
        });

        // 7. Admin cannot convert built-in admin to LDAP
        response = target().path("/user/admin").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("auth_source", "LDAP")));
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));
        JsonObject adminView = target().path("/user/admin").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("INTERNAL", adminView.getString("auth_source"));
        Assert.assertFalse(adminView.getBoolean("is_ldap"));

        // 8. Admin converts ldap_user back to INTERNAL and assigns new password
        response = target().path("/user/ldap_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("auth_source", "INTERNAL")
                        .param("password", "newpassword123")));
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));

        // Check view again
        view = target().path("/user/ldap_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("INTERNAL", view.getString("auth_source"));
        Assert.assertFalse(view.getBoolean("is_ldap"));

        // User can now log in with the new local password!
        String newLoginToken = clientUtil.login("ldap_user", "newpassword123", false);
        Assert.assertNotNull(newLoginToken);

        // Clean up
        target().path("/user/ldap_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
    }

    /**
     * Test user offboarding, session revocation, deletion guardrails, and asset handover.
     */
    @Test
    public void testUserAssetTransferAndOffboarding() throws Exception {
        String adminToken = adminToken();

        // 1. Create departing_user and successor_user (with tight quota of 10 bytes to test auto-expansion)
        clientUtil.createUser("departing_user");
        String departingToken = clientUtil.login("departing_user");

        target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("username", "successor_user")
                        .param("email", "successor@docs.com")
                        .param("password", "12345678")
                        .param("storage_quota", "10")), JsonObject.class);
        String successorToken = clientUtil.login("successor_user");

        // 2. departing_user creates a tag, a document, and uploads a file
        JsonObject tagJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, departingToken)
                .put(Entity.form(new Form()
                        .param("name", "ProjectAlpha")
                        .param("color", "#ff0000")), JsonObject.class);
        String tagId = tagJson.getString("id");

        JsonObject docJson = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, departingToken)
                .put(Entity.form(new Form()
                        .param("title", "Handover Spec Document")
                        .param("language", "eng")
                        .param("tags", tagId)), JsonObject.class);
        String docId = docJson.getString("id");

        String fileId = clientUtil.addFileToDocument(FILE_EINSTEIN_ROOSEVELT_LETTER_PNG,
                departingToken, docId);

        // 2b. admin creates a tag and grants departing_user WRITE permission on it
        JsonObject adminTagJson = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("name", "AdminManagedTag")
                        .param("color", "#00aa00")), JsonObject.class);
        String adminTagId = adminTagJson.getString("id");

        target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("source", adminTagId)
                        .param("perm", "WRITE")
                        .param("target", "departing_user")
                        .param("type", "USER")), JsonObject.class);

        // 3. Check GET /user/departing_user asset counts
        JsonObject departingView = target().path("/user/departing_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(1L, departingView.getJsonNumber("document_count").longValue());
        Assert.assertEquals(1L, departingView.getJsonNumber("tag_count").longValue());
        Assert.assertEquals(1L, departingView.getJsonNumber("file_count").longValue());
        long transferredBytes = departingView.getJsonNumber("storage_current").longValue();
        Assert.assertTrue(transferredBytes > 10L);

        // 4. Deleting departing_user while holding assets must be blocked with UserHasAssets
        Response deleteResp = target().path("/user/departing_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(deleteResp.getStatus()));
        JsonObject errJson = deleteResp.readEntity(JsonObject.class);
        Assert.assertEquals("UserHasAssets", errJson.getString("type"));

        // 5. Disable departing_user -> active session token is immediately invalidated & hidden from ACL search
        target().path("/user/departing_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("disabled", "true")), JsonObject.class);

        JsonObject sessionCheck = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, departingToken)
                .get(JsonObject.class);
        Assert.assertTrue("Disabled user session must be invalidated immediately", sessionCheck.getBoolean("anonymous"));

        JsonObject aclTargets = target().path("/acl/target/search")
                .queryParam("search", "departing")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(0, aclTargets.getJsonArray("users").size());

        // 6. Transfer all assets from departing_user to successor_user
        JsonObject transferResp = target().path("/user/departing_user/transfer").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("target_username", "successor_user")), JsonObject.class);
        Assert.assertEquals("ok", transferResp.getString("status"));
        Assert.assertEquals(1, transferResp.getInt("transferred_tags"));
        Assert.assertEquals(1, transferResp.getInt("transferred_documents"));
        Assert.assertEquals(1, transferResp.getInt("transferred_files"));

        // 7. Verify departing_user now has 0 assets and 0 storage_current
        departingView = target().path("/user/departing_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(0L, departingView.getJsonNumber("document_count").longValue());
        Assert.assertEquals(0L, departingView.getJsonNumber("tag_count").longValue());
        Assert.assertEquals(0L, departingView.getJsonNumber("file_count").longValue());
        Assert.assertEquals(0L, departingView.getJsonNumber("storage_current").longValue());

        // 8. Verify successor_user has the assets and auto-expanded storage_quota
        JsonObject successorView = target().path("/user/successor_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(1L, successorView.getJsonNumber("document_count").longValue());
        Assert.assertEquals(1L, successorView.getJsonNumber("tag_count").longValue());
        Assert.assertEquals(1L, successorView.getJsonNumber("file_count").longValue());
        Assert.assertEquals(transferredBytes, successorView.getJsonNumber("storage_current").longValue());
        Assert.assertEquals(10L + transferredBytes, successorView.getJsonNumber("storage_quota").longValue());

        // 9. Verify both successor_user and departing_user personal ACLs exist on the document
        JsonObject docDetail = target().path("/document/" + docId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, successorToken)
                .get(JsonObject.class);
        Assert.assertEquals("successor_user", docDetail.getString("creator"));
        Assert.assertTrue(docDetail.getBoolean("writable"));
        JsonArray acls = docDetail.getJsonArray("acls");
        boolean hasDepartingAcl = false;
        boolean hasSuccessorWriteAcl = false;
        for (int i = 0; i < acls.size(); i++) {
            JsonObject acl = acls.getJsonObject(i);
            if ("USER".equals(acl.getString("type")) && "departing_user".equals(acl.getString("name"))) {
                hasDepartingAcl = true;
            }
            if ("USER".equals(acl.getString("type")) && "successor_user".equals(acl.getString("name"))
                    && "WRITE".equals(acl.getString("perm"))) {
                hasSuccessorWriteAcl = true;
            }
        }
        Assert.assertTrue("Original owner's personal ACL should be preserved", hasDepartingAcl);
        Assert.assertTrue("Target owner must have direct WRITE ACL", hasSuccessorWriteAcl);

        // 9b. Verify successor_user inherited READ and WRITE permission on adminTagId (created by admin, granted to departing_user)
        JsonObject adminTagDetail = target().path("/tag/" + adminTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, successorToken)
                .get(JsonObject.class);
        Assert.assertTrue("Successor user must inherit WRITE permission on admin's tag", adminTagDetail.getBoolean("writable"));
        Response updateAdminTagResp = target().path("/tag/" + adminTagId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, successorToken)
                .post(Entity.form(new Form()
                        .param("name", "AdminManagedTagUpdated")
                        .param("color", "#00bb00")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(updateAdminTagResp.getStatus()));

        // 10. Delete departing_user now succeeds since asset count is 0
        deleteResp = target().path("/user/departing_user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(deleteResp.getStatus()));

        // 11. Verify successor_user can still download the physical file after departing_user is deleted
        Response fileDataResp = target().path("/file/" + fileId + "/data").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, successorToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(fileDataResp.getStatus()));
        byte[] fileBytes = fileDataResp.readEntity(byte[].class);
        Assert.assertTrue(fileBytes.length > 0);
    }
}
