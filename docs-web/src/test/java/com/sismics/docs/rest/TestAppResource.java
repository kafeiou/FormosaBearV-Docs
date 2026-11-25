package com.sismics.docs.rest;

import java.io.File;

import com.google.common.io.Resources;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.core.partition.impl.avl.AvlPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.junit.Assert;
import org.junit.Test;


/**
 * Test the app resource.
 *
 * @author jtremeaux
 */
public class TestAppResource extends BaseJerseyTest {
    /**
     * Test the API resource.
     */

    // Record if config has been changed by previous test runs
    private static boolean configInboxChanged = false;
    private static boolean configSmtpChanged = false;
    private static boolean configLdapChanged = false;

    @Test
    public void testAppResource() {
        // Login admin
        String adminToken = adminToken();

        // Check the application info
        JsonObject json = target().path("/app").request()
                .get(JsonObject.class);
        Assert.assertNotNull(json.getString("current_version"));
        Assert.assertNotNull(json.getString("min_version"));
        Long freeMemory = json.getJsonNumber("free_memory").longValue();
        Assert.assertTrue(freeMemory > 0);
        Long totalMemory = json.getJsonNumber("total_memory").longValue();
        Assert.assertTrue(totalMemory > 0 && totalMemory > freeMemory);
        Assert.assertEquals(0, json.getJsonNumber("queued_tasks").intValue());
        Assert.assertFalse(json.getBoolean("guest_login"));
        Assert.assertFalse(json.getBoolean("ocr_enabled"));
        Assert.assertEquals("eng", json.getString("default_language"));
        Assert.assertTrue(json.containsKey("global_storage_current"));
        Assert.assertTrue(json.getJsonNumber("active_user_count").longValue() > 0);

        // Rebuild Lucene index (with OCR mode MISSING) and verify status endpoint
        Response response = target().path("/app/batch/reindex").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("ocr_mode", "MISSING")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        JsonObject reindexStatus = target().path("/app/batch/reindex/status").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("MISSING", reindexStatus.getString("ocr_mode"));
        Assert.assertTrue(reindexStatus.containsKey("phase"));
        Assert.assertTrue(reindexStatus.containsKey("percent"));
        Assert.assertTrue(reindexStatus.containsKey("errors"));

        // Non-admin cannot read reindex status
        Response unauthStatus = target().path("/app/batch/reindex/status").request().get();
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(unauthStatus.getStatus()));

        // Verify runtime SBOM endpoint & exports (admin only)
        Response unauthSbom = target().path("/app/sbom").request().get();
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(unauthSbom.getStatus()));

        JsonObject sbom = target().path("/app/sbom").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertTrue(sbom.containsKey("environment"));
        Assert.assertTrue(sbom.getJsonObject("environment").getString("java_version").length() > 0);
        Assert.assertTrue(sbom.getJsonArray("backend_libraries").size() > 0);
        Assert.assertFalse(sbom.containsKey("languages"));

        String cdxJson = target().path("/app/sbom/export").queryParam("format", "cyclonedx").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(String.class);
        Assert.assertTrue(cdxJson.contains("\"bomFormat\":\"CycloneDX\""));
        Assert.assertTrue(cdxJson.contains("\"specVersion\":\"1.5\""));

        String csv = target().path("/app/sbom/export").queryParam("format", "csv").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(String.class);
        Assert.assertTrue(csv.contains("Category,Group,Name,Version,Status"));

        // Clean storage
        response = target().path("/app/batch/clean_storage").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        // Change the default language
        response = target().path("/app/config").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("default_language", "fra")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        // Check the application info
        json = target().path("/app").request()
                .get(JsonObject.class);
        Assert.assertEquals("fra", json.getString("default_language"));

        // Change the default language
        response = target().path("/app/config").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("default_language", "eng")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        // Check the application info
        json = target().path("/app").request()
                .get(JsonObject.class);
        Assert.assertEquals("eng", json.getString("default_language"));
    }

    /**
     * Test the log resource.
     */
    @Test
    public void testLogResource() {
        // Login admin
        String adminToken = adminToken();

        // Check the logs (page 1)
        JsonObject json = target().path("/app/log")
                .queryParam("level", "DEBUG")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray logs = json.getJsonArray("logs");
        Assert.assertTrue(logs.size() > 0);
        Long date1 = logs.getJsonObject(0).getJsonNumber("date").longValue();
        Long date2 = logs.getJsonObject(9).getJsonNumber("date").longValue();
        Assert.assertTrue(date1 >= date2);

        // Check the logs (page 2)
        json = target().path("/app/log")
                .queryParam("offset",  "10")
                .queryParam("level", "DEBUG")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        logs = json.getJsonArray("logs");
        Assert.assertTrue(logs.size() > 0);
        Long date3 = logs.getJsonObject(0).getJsonNumber("date").longValue();
        Long date4 = logs.getJsonObject(9).getJsonNumber("date").longValue();
        Assert.assertTrue(date3 >= date4);
    }

    /**
     * Test the guest login.
     */
    @Test
    public void testGuestLogin() {
        // Login admin
        String adminToken = adminToken();

        // Try to login as guest
        Response response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "guest")));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Enable guest login
        target().path("/app/guest_login").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "true")), JsonObject.class);

        // Login as guest
        String guestToken = clientUtil.login("guest", "", false);

        // Guest cannot delete himself
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        // Guest cannot see opened sessions
        JsonObject json = target().path("/user/session").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .get(JsonObject.class);
        Assert.assertEquals(0, json.getJsonArray("sessions").size());

        // Guest cannot delete opened sessions
        response = target().path("/user/session").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .delete();
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest cannot enable TOTP
        response = target().path("/user/enable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest cannot disable TOTP
        response = target().path("/user/disable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest cannot update itself
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest can see its documents
        target().path("/document/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .get(JsonObject.class);

        // Disable guest login (clean up state)
        target().path("/app/guest_login").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "false")), JsonObject.class);
    }

    /**
     * Test the ocr setting
     */
    @Test
    public void testOcrSetting() {
        // Login admin
        String adminToken = adminToken();

        // Get OCR configuration
        JsonObject json = target().path("/app/ocr").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        if (!configInboxChanged) {
            Assert.assertFalse(json.getBoolean("enabled"));
        }

        // Change OCR configuration
        target().path("/app/ocr").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "true")
                ), JsonObject.class);
        configInboxChanged = true;

        // Get OCR configuration
        json = target().path("/app/ocr").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("enabled"));
    }

    /**
     * Test SMTP configuration changes.
     */
    @Test
    public void testSmtpConfiguration() {
        // Login admin
        String adminToken = adminToken();

        // Get SMTP configuration
        JsonObject json = target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        if (!configSmtpChanged) {
                Assert.assertTrue(json.isNull("hostname"));
                Assert.assertTrue(json.isNull("port"));
                Assert.assertTrue(json.isNull("username"));
                Assert.assertTrue(json.isNull("password"));
                Assert.assertTrue(json.isNull("from"));
        }

        // Change SMTP configuration
        target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("hostname", "smtp.sismics.com")
                        .param("port", "1234")
                        .param("username", "sismics")
                        .param("from", "contact@sismics.com")
                ), JsonObject.class);
        configSmtpChanged = true;

        // Get SMTP configuration
        json = target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("smtp.sismics.com", json.getString("hostname"));
        Assert.assertEquals(1234, json.getInt("port"));
        Assert.assertEquals("sismics", json.getString("username"));
        Assert.assertTrue(json.isNull("password"));
        Assert.assertEquals("contact@sismics.com", json.getString("from"));
    }

    /**
     * Test inbox scanning.
     */
    @Test
    public void testInbox() {
        // Login admin
        String adminToken = adminToken();

        // Create a tag
        JsonObject json = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("name", "Inbox")
                        .param("color", "#ff0000")), JsonObject.class);
        String tagInboxId = json.getString("id");

        // Get inbox configuration
        json = target().path("/app/config_inbox").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonObject lastSync = json.getJsonObject("last_sync");
        if (!configInboxChanged) {
                Assert.assertFalse(json.getBoolean("enabled"));
                Assert.assertEquals("", json.getString("hostname"));
                Assert.assertEquals(993, json.getJsonNumber("port").intValue());
                Assert.assertEquals("", json.getString("username"));
                Assert.assertEquals("", json.getString("password"));
                Assert.assertEquals("INBOX", json.getString("folder"));
                Assert.assertEquals("", json.getString("tag"));
                Assert.assertTrue(lastSync.isNull("date"));
                Assert.assertTrue(lastSync.isNull("error"));
                Assert.assertEquals(0, lastSync.getJsonNumber("count").intValue());
        }

        // Change inbox configuration
        target().path("/app/config_inbox").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "true")
                        .param("starttls", "false")
                        .param("autoTagsEnabled", "false")
                        .param("deleteImported", "false")
                        .param("hostname", "localhost")
                        .param("port", "9755")
                        .param("username", "test@sismics.com")
                        .param("password", "12345678")
                        .param("folder", "INBOX")
                        .param("tag", tagInboxId)
                ), JsonObject.class);
        configInboxChanged = true;

        // Get inbox configuration
        json = target().path("/app/config_inbox").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("enabled"));
        Assert.assertEquals("localhost", json.getString("hostname"));
        Assert.assertEquals(9755, json.getInt("port"));
        Assert.assertEquals("test@sismics.com", json.getString("username"));
        Assert.assertEquals("12345678", json.getString("password"));
        Assert.assertEquals("INBOX", json.getString("folder"));
        Assert.assertEquals(tagInboxId, json.getString("tag"));

        ServerSetup serverSetupSmtp = new ServerSetup(9754, null, ServerSetup.PROTOCOL_SMTP);
        ServerSetup serverSetupImap = new ServerSetup(9755, null, ServerSetup.PROTOCOL_IMAP);
        GreenMail greenMail = new GreenMail(new ServerSetup[] { serverSetupSmtp, serverSetupImap });
        greenMail.setUser("test@sismics.com", "12345678");
        greenMail.start();

        // Test the inbox
        json = target().path("/app/test_inbox").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()), JsonObject.class);
        Assert.assertEquals(0, json.getJsonNumber("count").intValue());

        // Send an email
        GreenMailUtil.sendTextEmail("test@sismics.com", "test@sismicsdocs.com", "Test email 1", "Test content 1", serverSetupSmtp);

        // Trigger an inbox sync
        AppContext.getInstance().getInboxService().syncInbox();

        // Search for added documents
        json = target().path("/document/list")
                .queryParam("search", "tag:Inbox full:content")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, json.getJsonArray("documents").size());

        // Get inbox configuration
        json = target().path("/app/config_inbox").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        lastSync = json.getJsonObject("last_sync");
        Assert.assertFalse(lastSync.isNull("date"));
        Assert.assertTrue(lastSync.isNull("error"));
        Assert.assertEquals(1, lastSync.getJsonNumber("count").intValue());

        // Trigger an inbox sync
        AppContext.getInstance().getInboxService().syncInbox();

        // Search for added documents
        json = target().path("/document/list")
                .queryParam("search", "tag:Inbox full:content")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, json.getJsonArray("documents").size());

        // Get inbox configuration
        json = target().path("/app/config_inbox").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        lastSync = json.getJsonObject("last_sync");
        Assert.assertFalse(lastSync.isNull("date"));
        Assert.assertTrue(lastSync.isNull("error"));
        Assert.assertEquals(0, lastSync.getJsonNumber("count").intValue());

        greenMail.stop();
    }

    /**
     * Test the LDAP authentication.
     */
    @Test
    public void testLdapAuthentication() throws Exception {
        // Start LDAP server
        final DirectoryServiceFactory factory = new DefaultDirectoryServiceFactory();
        factory.init("Test");

        final DirectoryService directoryService = factory.getDirectoryService();
        directoryService.getChangeLog().setEnabled(false);
        directoryService.setShutdownHookEnabled(true);

        final Partition partition = new AvlPartition(directoryService.getSchemaManager());
        partition.setId("Test");
        partition.setSuffixDn(new Dn(directoryService.getSchemaManager(), "o=TEST"));
        partition.initialize();
        directoryService.addPartition(partition);

        final LdapServer ldapServer = new LdapServer();
        ldapServer.setTransports(new TcpTransport("localhost", 11389));
        ldapServer.setDirectoryService(directoryService);

        directoryService.startup();
        ldapServer.start();

        // Load test data in LDAP
        new LdifFileLoader(directoryService.getAdminSession(), new File(Resources.getResource("test.ldif").getFile()), null).execute();

        // Login admin
        String adminToken = adminToken();

        // Get the LDAP configuration
        JsonObject json = target().path("/app/config_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        if (!configLdapChanged) {
                Assert.assertFalse(json.getBoolean("enabled"));
        }

        // Change LDAP configuration
        target().path("/app/config_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "true")
                        .param("host", "localhost")
                        .param("port", "11389")
                        .param("usessl", "false")
                        .param("admin_dn", "uid=admin,ou=system")
                        .param("admin_password", "secret")
                        .param("base_dn", "o=TEST")
                        .param("filter", "(&(objectclass=inetOrgPerson)(uid=USERNAME))")
                        .param("default_email", "devnull@teedy.io")
                        .param("default_storage", "100000000")
                ), JsonObject.class);
        configLdapChanged = true;

        // Get the LDAP configuration
        json = target().path("/app/config_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("enabled"));
        Assert.assertEquals("localhost", json.getString("host"));
        Assert.assertEquals(11389, json.getJsonNumber("port").intValue());
        Assert.assertEquals("uid=admin,ou=system", json.getString("admin_dn"));
        Assert.assertEquals("secret", json.getString("admin_password"));
        Assert.assertEquals("o=TEST", json.getString("base_dn"));
        Assert.assertEquals("(&(objectclass=inetOrgPerson)(uid=USERNAME))", json.getString("filter"));
        Assert.assertEquals("devnull@teedy.io", json.getString("default_email"));
        Assert.assertEquals(100000000L, json.getJsonNumber("default_storage").longValue());

        // Login with a LDAP user
        String ldapTopen = clientUtil.login("ldap1", "secret", false);

        // Check user informations
        json = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, ldapTopen)
                .get(JsonObject.class);
        Assert.assertEquals("ldap1@teedy.io", json.getString("email"));

        // List all documents
        json = target().path("/document/list")
                .queryParam("sort_column", 3)
                .queryParam("asc", true)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, ldapTopen)
                .get(JsonObject.class);
        JsonArray documents = json.getJsonArray("documents");
        Assert.assertEquals(0, documents.size());

        // Multi-LDAP test: Configure 2 LDAP servers (Server 1 with non-matching filter, Server 2 matching)
        String serversJson = "["
                + "{\"name\":\"Branch LDAP (No match)\",\"enabled\":true,\"host\":\"localhost\",\"port\":11389,\"usessl\":false,\"usestarttls\":true,"
                + "\"admin_dn\":\"uid=admin,ou=system\",\"admin_password\":\"secret\",\"base_dn\":\"o=TEST\","
                + "\"filter\":\"(&(objectclass=inetOrgPerson)(uid=NONEXISTENT_USERNAME))\",\"default_email\":\"branch@teedy.io\",\"default_storage\":100000000},"
                + "{\"name\":\"HQ LDAP (Match)\",\"enabled\":true,\"host\":\"localhost\",\"port\":11389,\"usessl\":false,\"usestarttls\":false,"
                + "\"admin_dn\":\"uid=admin,ou=system\",\"admin_password\":\"secret\",\"base_dn\":\"o=TEST\","
                + "\"filter\":\"(&(objectclass=inetOrgPerson)(uid=USERNAME))\",\"default_email\":\"hq@teedy.io\",\"default_storage\":200000000}"
                + "]";
        Response multiResp = target().path("/app/config_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("servers_json", serversJson)));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(multiResp.getStatus()));

        json = target().path("/app/config_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray servers = json.getJsonArray("servers");
        Assert.assertEquals(2, servers.size());
        Assert.assertEquals("Branch LDAP (No match)", servers.getJsonObject(0).getString("name"));
        Assert.assertTrue(servers.getJsonObject(0).getBoolean("usestarttls"));
        Assert.assertEquals("HQ LDAP (Match)", servers.getJsonObject(1).getString("name"));
        Assert.assertFalse(servers.getJsonObject(1).getBoolean("usestarttls"));

        // Test LDAP connection endpoint (Issue #788 & #789)
        // 1. Non-admin user is forbidden
        Response forbiddenTestResp = target().path("/app/test_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, ldapTopen)
                .post(Entity.form(new Form()
                        .param("host", "localhost")
                        .param("port", "11389")
                        .param("admin_dn", "uid=admin,ou=system")
                        .param("admin_password", "secret")));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(forbiddenTestResp.getStatus()));

        // 2. Valid LDAP connection & base_dn succeeds
        Response okTestResp = target().path("/app/test_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("host", "localhost")
                        .param("port", "11389")
                        .param("usessl", "false")
                        .param("usestarttls", "false")
                        .param("admin_dn", "uid=admin,ou=system")
                        .param("admin_password", "secret")
                        .param("base_dn", "o=TEST")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(okTestResp.getStatus()));
        Assert.assertEquals("ok", okTestResp.readEntity(JsonObject.class).getString("status"));

        // 3. Invalid password returns 400 with error status
        Response badPassTestResp = target().path("/app/test_ldap").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("host", "localhost")
                        .param("port", "11389")
                        .param("usessl", "false")
                        .param("usestarttls", "false")
                        .param("admin_dn", "uid=admin,ou=system")
                        .param("admin_password", "wrong_secret")
                        .param("base_dn", "o=TEST")));
        Assert.assertEquals(Status.BAD_REQUEST, Status.fromStatusCode(badPassTestResp.getStatus()));
        Assert.assertEquals("error", badPassTestResp.readEntity(JsonObject.class).getString("status"));

        // Authenticate via fallback to 2nd LDAP server
        String multiLdapToken = clientUtil.login("ldap1", "secret", false);
        Assert.assertNotNull(multiLdapToken);

        // Stop LDAP server
        ldapServer.stop();
        directoryService.shutdown();
    }

    /**
     * Test Base URL configuration and SMTP from_name setting.
     */
    @Test
    public void testBaseUrlAndMailSettings() {
        String adminToken = adminToken();

        // 1. Initial check: base_url defaults to http://localhost:8080
        JsonObject json = target().path("/app").request().get(JsonObject.class);
        Assert.assertEquals("http://localhost:8080", json.getString("base_url"));

        // 2. Check SMTP from_name defaults to FormosaBearV
        json = target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("FormosaBearV", json.getString("from_name"));

        // 3. Update Base URL with trailing slashes and missing http protocol to test auto-normalization
        Response response = target().path("/app/config").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("default_language", "eng")
                        .param("base_url", "my-docs.example.com:8443///")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        json = target().path("/app").request().get(JsonObject.class);
        Assert.assertEquals("http://my-docs.example.com:8443", json.getString("base_url"));

        // 4. Update SMTP from_name
        response = target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("from_name", "Taiwan Bear Docs Center")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        json = target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("Taiwan Bear Docs Center", json.getString("from_name"));

        // 5. Restore back to default
        target().path("/app/config").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("default_language", "eng")
                        .param("base_url", "http://localhost:8080")));
        target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("from_name", "FormosaBearV")));
    }

    /**
     * Test document preview watermark configuration.
     */
    @Test
    public void testPreviewWatermarkSettings() {
        String adminToken = adminToken();

        // 1. Default values in GET /app
        JsonObject json = target().path("/app").request().get(JsonObject.class);
        Assert.assertFalse(json.getBoolean("preview_watermark_enabled"));
        Assert.assertEquals("dd MMM yyyy", json.getString("preview_watermark_date_format"));

        // 2. Non-admin user cannot modify watermark settings
        clientUtil.createUser("watermark_normal_user");
        String userToken = clientUtil.login("watermark_normal_user");
        Response forbiddenResp = target().path("/app/config_watermark").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .post(Entity.form(new Form()
                        .param("enabled", "true")
                        .param("date_format", "MMM dd, yyyy")));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(forbiddenResp.getStatus()));

        // 3. Admin enables watermark and updates date format
        Response response = target().path("/app/config_watermark").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "true")
                        .param("date_format", "MMM dd, yyyy")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        json = target().path("/app").request().get(JsonObject.class);
        Assert.assertTrue(json.getBoolean("preview_watermark_enabled"));
        Assert.assertEquals("MMM dd, yyyy", json.getString("preview_watermark_date_format"));

        // 4. Empty date_format falls back to default "dd MMM yyyy"
        response = target().path("/app/config_watermark").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "false")
                        .param("date_format", "   ")));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));

        json = target().path("/app").request().get(JsonObject.class);
        Assert.assertFalse(json.getBoolean("preview_watermark_enabled"));
        Assert.assertEquals("dd MMM yyyy", json.getString("preview_watermark_date_format"));
    }

    /**
     * Test Tesseract path normalization and availability check endpoint.
     */
    @Test
    public void testTesseractCheckAndPathResolution() throws Exception {
        String adminToken = adminToken();
        clientUtil.createUser("ocr_check_normal_user");
        String userToken = clientUtil.login("ocr_check_normal_user");

        // 1. Directory path auto-appends executable and strips quotes
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("tess-dir-test");
        try {
            String resolvedFromQuotedDir = com.sismics.docs.core.util.ocr.TesseractOcrProvider
                    .resolveTesseractCommand("\"" + tempDir.toAbsolutePath() + "\"");
            Assert.assertTrue(resolvedFromQuotedDir.startsWith(tempDir.toAbsolutePath().toString()));
            Assert.assertTrue(resolvedFromQuotedDir.endsWith("tesseract.exe") || resolvedFromQuotedDir.endsWith("tesseract"));
        } finally {
            java.nio.file.Files.deleteIfExists(tempDir);
        }

        // 2. Non-admin is forbidden
        Response forbidden = target().path("/app/ocr/test-tesseract").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .post(Entity.form(new Form().param("tesseract_path", "")));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(forbidden.getStatus()));

        // 3. Admin check on non-existent quoted path returns ok=false with cleaned resolved_path
        JsonObject res = target().path("/app/ocr/test-tesseract").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("tesseract_path", "\"/nonexistent/path/tesseract.exe\"")), JsonObject.class);
        Assert.assertFalse(res.getBoolean("ok"));
        Assert.assertEquals("/nonexistent/path/tesseract.exe", res.getString("resolved_path"));
        Assert.assertFalse(res.getString("error", "").isEmpty());
        Assert.assertEquals(0, res.getJsonArray("languages").size());

        // 4. Arbitrary executable commands (CWE-78 / CWE-88) must be rejected before ProcessBuilder
        for (String badCmd : new String[]{ "cmd.exe", "/bin/sh", "C:\\Windows\\System32\\cmd.exe", "..\\tesseract.exe" }) {
            JsonObject badRes = target().path("/app/ocr/test-tesseract").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                    .post(Entity.form(new Form().param("tesseract_path", badCmd)), JsonObject.class);
            Assert.assertFalse("Expected rejection for " + badCmd, badRes.getBoolean("ok"));
            Assert.assertFalse(badRes.getString("error", "").isEmpty());
        }
    }

    /**
     * Test SBOM persistent caching on first load and forced refresh.
     */
    @Test
    public void testSbomCachingAndRefresh() throws Exception {
        String adminToken = adminToken();
        clientUtil.createUser("sbom_normal_user");
        String userToken = clientUtil.login("sbom_normal_user");

        // Non-admin is forbidden
        Response forbidden = target().path("/app/sbom").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get();
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(forbidden.getStatus()));

        // First call generates and caches snapshot
        JsonObject first = target().path("/app/sbom").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String ts1 = first.getString("generated_at");
        Assert.assertFalse(ts1.isEmpty());

        Thread.sleep(20);

        // Second call without refresh returns exact same cached timestamp
        JsonObject second = target().path("/app/sbom").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(ts1, second.getString("generated_at"));

        Thread.sleep(20);

        // Refresh=true forces re-scan with a newer timestamp and updates cache
        JsonObject refreshed = target().path("/app/sbom").queryParam("refresh", true).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String ts2 = refreshed.getString("generated_at");
        Assert.assertNotEquals(ts1, ts2);

        // Subsequent non-refresh call returns the updated cached timestamp
        JsonObject third = target().path("/app/sbom").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(ts2, third.getString("generated_at"));
    }

    /**
     * Test search engine visibility (robots.txt & X-Robots-Tag) setting.
     */
    @Test
    public void testRobotsIndexingConfig() {
        String adminToken = adminToken();
        clientUtil.createUser("robots_normal_user");
        String userToken = clientUtil.login("robots_normal_user");

        // 1. Defaults to private (false)
        JsonObject app = target().path("/app").request().get(JsonObject.class);
        Assert.assertFalse(app.getBoolean("robots_indexing_enabled"));
        Assert.assertEquals("User-agent: *\nDisallow: /\n",
                com.sismics.docs.rest.filter.RobotsFilter.buildRobotsTxt(false));

        // 2. Non-admin is forbidden
        Response forbidden = target().path("/app/config_robots").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .post(Entity.form(new Form().param("enabled", "true")));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(forbidden.getStatus()));

        // 3. Admin switches to public (true)
        JsonObject pubRes = target().path("/app/config_robots").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("enabled", "true")), JsonObject.class);
        Assert.assertTrue(pubRes.getBoolean("robots_indexing_enabled"));

        app = target().path("/app").request().get(JsonObject.class);
        Assert.assertTrue(app.getBoolean("robots_indexing_enabled"));
        Assert.assertEquals("User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /share.html\n",
                com.sismics.docs.rest.filter.RobotsFilter.buildRobotsTxt(true));

        // 4. Admin switches back to private (false)
        JsonObject privRes = target().path("/app/config_robots").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("enabled", "false")), JsonObject.class);
        Assert.assertFalse(privRes.getBoolean("robots_indexing_enabled"));
    }

    /**
     * Test login blocking & public progress reporting during startup vs manual index rebuild.
     */
    @Test
    public void testReindexMaintenanceLock() {
        clientUtil.createUser("reindex_lock_user");
        try {
            // 1. Startup automatic rebuild blocks ALL logins (including admin)
            Assert.assertTrue(com.sismics.docs.core.util.indexing.ReindexProgress.tryQueue(
                    com.sismics.docs.core.event.RebuildIndexAsyncEvent.OcrMode.NONE, true));
            com.sismics.docs.core.util.indexing.ReindexProgress.setTotals(10, 10);
            com.sismics.docs.core.util.indexing.ReindexProgress.setPhase(
                    com.sismics.docs.core.util.indexing.ReindexProgress.Phase.INDEXING_DOCUMENTS);
            com.sismics.docs.core.util.indexing.ReindexProgress.addProcessedDocuments(5, "secret.pdf");

            // /api/app exposes progress counts & percent without leaking document titles
            JsonObject appJson = target().path("/app").request().get(JsonObject.class);
            JsonObject reindexing = appJson.getJsonObject("reindexing");
            Assert.assertTrue(reindexing.getBoolean("running"));
            Assert.assertTrue(reindexing.getBoolean("startup_maintenance"));
            Assert.assertEquals(5, reindexing.getInt("processed"));
            Assert.assertEquals(20, reindexing.getInt("total"));
            Assert.assertEquals(25, reindexing.getInt("percent"));
            Assert.assertFalse(reindexing.containsKey("current_item"));

            Response adminBlocked = target().path("/user/login").request()
                    .post(Entity.form(new Form().param("username", "admin").param("password", "superSecure")));
            Assert.assertEquals(Status.SERVICE_UNAVAILABLE.getStatusCode(), adminBlocked.getStatus());
            Assert.assertEquals("ReindexingInProgress", adminBlocked.readEntity(JsonObject.class).getString("type"));

            com.sismics.docs.core.util.indexing.ReindexProgress.complete();

            // 2. Manual rebuild blocks non-admin users while allowing admin login
            Assert.assertTrue(com.sismics.docs.core.util.indexing.ReindexProgress.tryQueue(
                    com.sismics.docs.core.event.RebuildIndexAsyncEvent.OcrMode.NONE, false));

            Response userBlocked = target().path("/user/login").request()
                    .post(Entity.form(new Form().param("username", "reindex_lock_user").param("password", "12345678")));
            Assert.assertEquals(Status.SERVICE_UNAVAILABLE.getStatusCode(), userBlocked.getStatus());

            Response adminAllowed = target().path("/user/login").request()
                    .post(Entity.form(new Form().param("username", "admin").param("password", "superSecure")));
            Assert.assertEquals(Status.OK.getStatusCode(), adminAllowed.getStatus());
        } finally {
            com.sismics.docs.core.util.indexing.ReindexProgress.complete();
        }
    }

    /**
     * Test storage/DB integrity scan and selective cleanup endpoints (ADMIN only, traversal-safe).
     */
    @Test
    public void testStorageIntegrityScanAndClean() throws Exception {
        String adminToken = adminToken();
        clientUtil.createUser("integrity_normal_user");
        String userToken = clientUtil.login("integrity_normal_user");

        // 1. Non-admin is forbidden
        Response forbiddenScan = target().path("/app/integrity/scan").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .get();
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(forbiddenScan.getStatus()));

        Response forbiddenClean = target().path("/app/integrity/clean").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, userToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.FORBIDDEN, Status.fromStatusCode(forbiddenClean.getStatus()));

        // 2. Create an orphaned file on disk under storage/
        java.nio.file.Path storageRoot = com.sismics.docs.core.util.DirectoryUtil.getBaseDataDirectory().resolve("storage");
        java.nio.file.Path orphanDir = storageRoot.resolve("orphan_test_dir");
        java.nio.file.Files.createDirectories(orphanDir);
        java.nio.file.Path orphanFile = orphanDir.resolve("stray_file.bin");
        java.nio.file.Files.write(orphanFile, "orphan-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            // 3. Scan detects the orphaned disk file
            JsonObject scan = target().path("/app/integrity/scan").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                    .get(JsonObject.class);
            boolean foundOrphan = false;
            for (int i = 0; i < scan.getJsonArray("orphaned_disk_files").size(); i++) {
                JsonObject item = scan.getJsonArray("orphaned_disk_files").getJsonObject(i);
                if ("orphan_test_dir/stray_file.bin".equals(item.getString("relative_path"))) {
                    foundOrphan = true;
                    Assert.assertEquals(14, item.getInt("size"));
                }
            }
            Assert.assertTrue("Expected orphaned disk file to be detected", foundOrphan);

            // 4. Path traversal attempt is safely skipped
            JsonObject badClean = target().path("/app/integrity/clean").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                    .post(Entity.form(new Form().param("disk_paths", "../outside.txt")), JsonObject.class);
            Assert.assertEquals(0, badClean.getInt("cleaned_disk_files"));
            Assert.assertEquals(1, badClean.getInt("skipped"));

            // 5. Cleaning the selected orphan deletes it and cleans empty parent dir
            JsonObject clean = target().path("/app/integrity/clean").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                    .post(Entity.form(new Form().param("disk_paths", "orphan_test_dir/stray_file.bin")), JsonObject.class);
            Assert.assertEquals("ok", clean.getString("status"));
            Assert.assertEquals(1, clean.getInt("cleaned_disk_files"));
            Assert.assertEquals(14, clean.getInt("reclaimed_bytes"));
            Assert.assertFalse(java.nio.file.Files.exists(orphanFile));
        } finally {
            java.nio.file.Files.deleteIfExists(orphanFile);
            java.nio.file.Files.deleteIfExists(orphanDir);
        }
    }
}
