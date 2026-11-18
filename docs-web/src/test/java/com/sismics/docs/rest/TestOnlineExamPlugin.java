package com.sismics.docs.rest;

import com.sismics.util.filter.TokenBasedSecurityFilter;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * End-to-end integration test for the Online Exam plugin:
 * three-tier roles (Top Admin > Subject Teacher Groups > Examinees), exam isolation,
 * equal 100-pt grading, and localized summary PDF export with date range.
 */
public class TestOnlineExamPlugin extends BaseJerseyTest {
    @Test
    public void testOnlineExamEndToEnd() throws Exception {
        String adminToken = adminToken();
        clientUtil.createUser("exam_mgr");
        clientUtil.createUser("teacher1");
        clientUtil.createUser("teacher2");
        clientUtil.createUser("exam_alice");
        clientUtil.createUser("exam_bob");
        clientUtil.createUser("exam_disabled");
        target().path("/user/exam_disabled").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form().param("disabled", "true")), JsonObject.class);

        JsonObject usersResp = target().path("/user/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String disabledUserId = null;
        for (int i = 0; i < usersResp.getJsonArray("users").size(); i++) {
            JsonObject u = usersResp.getJsonArray("users").getJsonObject(i);
            if ("exam_disabled".equals(u.getString("username"))) {
                disabledUserId = u.getString("id");
            }
        }
        Assert.assertNotNull(disabledUserId);

        // Create two subject teacher groups: "OnlineExam01" (teacher1) and "OnlineExam02" (teacher2)
        target().path("/group").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form().param("name", "OnlineExam01")), JsonObject.class);
        target().path("/group").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form().param("name", "OnlineExam02")), JsonObject.class);

        target().path("/group/OnlineExam01").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form().param("username", "teacher1")), JsonObject.class);
        target().path("/group/OnlineExam02").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form().param("username", "teacher2")), JsonObject.class);

        String mgrToken = clientUtil.login("exam_mgr");
        String t1Token = clientUtil.login("teacher1");
        String t2Token = clientUtil.login("teacher2");
        String aliceToken = clientUtil.login("exam_alice");
        String bobToken = clientUtil.login("exam_bob");

        // 1. Upload & enable plugin-online-exam.zip
        Path zipPath = Paths.get("../docs-plugins/dist/plugin-online-exam.zip");
        if (!Files.exists(zipPath)) {
            zipPath = Paths.get("docs-plugins/dist/plugin-online-exam.zip");
        }
        byte[] zipBytes = Files.readAllBytes(zipPath);

        StreamDataBodyPart filePart = new StreamDataBodyPart(
                "file", new ByteArrayInputStream(zipBytes), "plugin-online-exam.zip", MediaType.APPLICATION_OCTET_STREAM_TYPE);
        FormDataMultiPart multiPart = new FormDataMultiPart();
        multiPart.bodyPart(filePart);
        Response upResp = target().path("/plugin/upload").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.entity(multiPart, multiPart.getMediaType()));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), upResp.getStatus());
        JsonObject installed = upResp.readEntity(JsonObject.class);
        JsonArray protectedTags = installed.getJsonArray("protected_tags");
        Assert.assertEquals(1, protectedTags.size());
        String mgmtTagId = protectedTags.getJsonObject(0).getString("tag_id");

        target().path("/plugin/online-exam/enable").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.entity(new Form(), MediaType.APPLICATION_FORM_URLENCODED_TYPE), JsonObject.class);

        // 1b. Verify first-enable provisioning (English hierarchical tags, groups, textbook doc + 16 localized PDFs, plain-text extraction, and bound exam)
        JsonObject adminExamsAfterEnable = target().path("/plugin/online-exam/action/exams").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, adminExamsAfterEnable.getJsonArray("exams").size());
        JsonObject demoExamSummary = adminExamsAfterEnable.getJsonArray("exams").getJsonObject(0);
        String demoExamId = demoExamSummary.getString("id");
        Assert.assertEquals("SEC-001", demoExamSummary.getString("exam_code"));
        Assert.assertEquals("Security Fundamentals Assessment", demoExamSummary.getString("title"));
        JsonArray demoRefDocs = demoExamSummary.getJsonArray("reference_documents");
        Assert.assertEquals(1, demoRefDocs.size());
        Assert.assertEquals("Security Fundamentals Guide", demoRefDocs.getJsonObject(0).getString("title"));
        String demoDocId = demoRefDocs.getJsonObject(0).getString("id");

        // Verify SEC-001 dynamically switches title, questions, and choices across locales (e.g., zh_TW and ja)
        JsonObject zhExams = target().path("/plugin/online-exam/action/exams").queryParam("lang", "zh_TW").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("資安基礎測驗", zhExams.getJsonArray("exams").getJsonObject(0).getString("title"));

        JsonObject jaTake = target().path("/plugin/online-exam/action/take_exam")
                .queryParam("examId", demoExamId).queryParam("lang", "ja").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("情報セキュリティ基礎試験", jaTake.getJsonObject("exam").getString("title"));
        Assert.assertTrue(jaTake.getJsonObject("exam").getJsonArray("questions").getJsonObject(0).getString("prompt").contains("情報セキュリティ基礎ガイド"));

        // Verify textbook document has multi-language description and all 16 localized PDF files attached
        JsonObject demoDocDetail = target().path("/document/" + demoDocId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String demoDocDesc = demoDocDetail.getString("description", "");
        Assert.assertTrue(demoDocDesc.contains("[EN]"));
        Assert.assertTrue(demoDocDesc.contains("[zh_TW]"));
        Assert.assertTrue(demoDocDesc.contains("[pt_BR]"));

        JsonObject demoDocFiles = target().path("/file/list").queryParam("id", demoDocId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(16, demoDocFiles.getJsonArray("files").size());
        String enFileId = null;
        String zhTwFileId = null;
        for (int i = 0; i < demoDocFiles.getJsonArray("files").size(); i++) {
            JsonObject f = demoDocFiles.getJsonArray("files").getJsonObject(i);
            if ("Security Fundamentals Guide (English).pdf".equals(f.getString("name"))) {
                enFileId = f.getString("id");
            } else if ("資安基礎講義（繁體中文）.pdf".equals(f.getString("name"))) {
                zhTwFileId = f.getString("id");
            }
        }
        Assert.assertNotNull(enFileId);
        Assert.assertNotNull(zhTwFileId);

        // Wait briefly for async FileProcessingAsyncListener to finish extracting PDF plain text ("eye" button content)
        for (int attempt = 0; attempt < 40; attempt++) {
            JsonObject checkFiles = target().path("/file/list").queryParam("id", demoDocId).request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                    .get(JsonObject.class);
            boolean anyProcessing = false;
            for (int i = 0; i < checkFiles.getJsonArray("files").size(); i++) {
                if (checkFiles.getJsonArray("files").getJsonObject(i).getBoolean("processing", false)) {
                    anyProcessing = true;
                    break;
                }
            }
            if (!anyProcessing) {
                break;
            }
            Thread.sleep(200);
        }

        String enPlainText = target().path("/file/" + enFileId + "/data").queryParam("size", "content").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(String.class);
        Assert.assertTrue("Expected extracted English plain text for eye button", enPlainText.contains("3-2-1"));

        String zhTwPlainText = target().path("/file/" + zhTwFileId + "/data").queryParam("size", "content").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(String.class);
        Assert.assertTrue("Expected extracted Traditional Chinese plain text for eye button", zhTwPlainText.contains("3-2-1"));

        // Verify hierarchical English tags "OnlineExamMaterials" -> "SecurityFundamentals"
        JsonObject allTagsAfterEnable = target().path("/tag/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        String demoRootTagId = null;
        String demoChildTagParentId = null;
        for (int i = 0; i < allTagsAfterEnable.getJsonArray("tags").size(); i++) {
            JsonObject t = allTagsAfterEnable.getJsonArray("tags").getJsonObject(i);
            if ("OnlineExamMaterials".equals(t.getString("name"))) {
                demoRootTagId = t.getString("id");
            } else if ("SecurityFundamentals".equals(t.getString("name"))) {
                demoChildTagParentId = t.getString("parent", "");
            }
        }
        Assert.assertNotNull(demoRootTagId);
        Assert.assertEquals(demoRootTagId, demoChildTagParentId);

        // Re-enabling is idempotent (does not duplicate demo exam)
        target().path("/plugin/online-exam/enable").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.entity(new Form(), MediaType.APPLICATION_FORM_URLENCODED_TYPE), JsonObject.class);
        JsonObject adminExamsAfterReEnable = target().path("/plugin/online-exam/action/exams").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals(1, adminExamsAfterReEnable.getJsonArray("exams").size());

        // Grant exam_mgr READ ACL on MANAGEMENT protected tag -> Top Admin
        target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("source", mgmtTagId)
                        .param("perm", "READ")
                        .param("target", "exam_mgr")
                        .param("type", "USER")), JsonObject.class);

        // 2. Top Admin authorizes "Online Exam 01" and "Online Exam 02" as teacher groups
        JsonObject teachersList = target().path("/plugin/online-exam/action/list_teachers").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, mgrToken)
                .get(JsonObject.class);
        String g1Id = null;
        String g2Id = null;
        for (int i = 0; i < teachersList.getJsonArray("groups").size(); i++) {
            JsonObject g = teachersList.getJsonArray("groups").getJsonObject(i);
            if ("OnlineExam01".equals(g.getString("name"))) g1Id = g.getString("id");
            if ("OnlineExam02".equals(g.getString("name"))) g2Id = g.getString("id");
        }
        Assert.assertNotNull(g1Id);
        Assert.assertNotNull(g2Id);

        target().path("/plugin/online-exam/action/set_teacher").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, mgrToken)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("target_type", "GROUP").add("target_id", g1Id).add("allow", true).build().toString()), JsonObject.class);
        target().path("/plugin/online-exam/action/set_teacher").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, mgrToken)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("target_type", "GROUP").add("target_id", g2Id).add("allow", true).build().toString()), JsonObject.class);

        // 3. Teacher 1 (Online Exam 01) creates Exam 1; Teacher 2 (Online Exam 02) creates Exam 2 (no tag_id needed!)
        String gift = "::Q1:: 台灣最高峰為何？ {\n  =玉山\n  ~雪山\n}\n\n"
                + "::Q2:: 多選題測試 {\n  ~%50%選項A\n  ~%50%選項B\n  ~%-50%選項C\n}\n\n"
                + "::Q3:: 浮水印需顯示當時考試時間。 {TRUE}\n";
        JsonObject save1 = Json.createObjectBuilder()
                .add("title", "科別一資安測驗")
                .add("exam_code", "SEC-101")
                .add("exam_version", "1.0")
                .add("total_score", 100)
                .add("retake_allowed", false)
                .add("gift", gift)
                .build();
        JsonObject saved1 = target().path("/plugin/online-exam/action/save_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(save1.toString()), JsonObject.class);
        String exam1Id = saved1.getJsonObject("exam").getString("id");
        Assert.assertTrue(saved1.getJsonObject("exam").getString("owner_label").contains("OnlineExam01"));

        JsonObject save2 = Json.createObjectBuilder()
                .add("title", "科別二法規測驗")
                .add("exam_code", "LAW-201")
                .add("exam_version", "1.0")
                .add("total_score", 100)
                .add("retake_allowed", true)
                .add("gift", gift)
                .build();
        JsonObject saved2 = target().path("/plugin/online-exam/action/save_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t2Token)
                .post(Entity.json(save2.toString()), JsonObject.class);
        String exam2Id = saved2.getJsonObject("exam").getString("id");
        Assert.assertTrue(saved2.getJsonObject("exam").getString("owner_label").contains("OnlineExam02"));

        // Teacher isolation: teacher1 only sees Exam 1; cannot toggle Exam 2; Top Admin sees both
        JsonObject t1Exams = target().path("/plugin/online-exam/action/exams").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .get(JsonObject.class);
        Assert.assertEquals(1, t1Exams.getJsonArray("exams").size());
        Assert.assertEquals(exam1Id, t1Exams.getJsonArray("exams").getJsonObject(0).getString("id"));

        Response t1ToggleExam2 = target().path("/plugin/online-exam/action/toggle_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(Json.createObjectBuilder().add("id", exam2Id).add("enabled", false).build().toString()));
        Assert.assertEquals(Response.Status.FORBIDDEN.getStatusCode(), t1ToggleExam2.getStatus());

        JsonObject mgrExams = target().path("/plugin/online-exam/action/exams").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, mgrToken)
                .get(JsonObject.class);
        Assert.assertEquals(3, mgrExams.getJsonArray("exams").size());

        // 4. Teacher 1 grants exam_alice permission on Exam 1; disabled users excluded & rejected
        JsonObject aclTargets = target().path("/plugin/online-exam/action/list_acl_targets")
                .queryParam("examId", exam1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .get(JsonObject.class);
        String aliceId = null;
        for (int i = 0; i < aclTargets.getJsonArray("users").size(); i++) {
            JsonObject u = aclTargets.getJsonArray("users").getJsonObject(i);
            Assert.assertNotEquals("exam_disabled", u.getString("username"));
            if ("exam_alice".equals(u.getString("username"))) {
                aliceId = u.getString("id");
            }
        }
        Assert.assertNotNull(aliceId);

        Response disabledAclResp = target().path("/plugin/online-exam/action/set_exam_acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("exam_id", exam1Id).add("target_type", "USER").add("target_id", disabledUserId).add("allow", true).build().toString()));
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), disabledAclResp.getStatus());

        target().path("/plugin/online-exam/action/set_exam_acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("exam_id", exam1Id).add("target_type", "USER").add("target_id", aliceId).add("allow", true).build().toString()), JsonObject.class);

        // Bob (no permission) cannot see Exam 1; Alice sees Exam 1 and takes it
        JsonObject bobExams = target().path("/plugin/online-exam/action/exams").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .get(JsonObject.class);
        Assert.assertEquals(0, bobExams.getJsonArray("exams").size());

        // 4b. Multi-document reference binding with strict READ permission filtering
        String sharedDocId = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .put(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("title", "SecGuide Shared")
                        .param("language", "eng")), JsonObject.class)
                .getString("id");
        Response aclAddResp = target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .put(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("source", sharedDocId)
                        .param("perm", "READ")
                        .param("target", "exam_alice")
                        .param("type", "USER")));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), aclAddResp.getStatus());

        String privateDocId = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .put(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("title", "SecGuide TeacherOnly")
                        .param("language", "eng")), JsonObject.class)
                .getString("id");

        String t2SecretDocId = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t2Token)
                .put(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("title", "Teacher2 Private Doc")
                        .param("language", "eng")), JsonObject.class)
                .getString("id");

        // Teacher 1 cannot bind Teacher 2's private document
        Response bindForbidden = target().path("/plugin/online-exam/action/set_exam_reference_documents").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("exam_id", exam1Id)
                        .add("document_id", t2SecretDocId)
                        .add("allow", true).build().toString()));
        Assert.assertEquals(Response.Status.FORBIDDEN.getStatusCode(), bindForbidden.getStatus());

        // Teacher 1 binds both sharedDocId and privateDocId to Exam 1
        target().path("/plugin/online-exam/action/set_exam_reference_documents").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("exam_id", exam1Id)
                        .add("reference_document_ids", Json.createArrayBuilder().add(sharedDocId).add(privateDocId))
                        .build().toString()), JsonObject.class);

        JsonObject t1RefList = target().path("/plugin/online-exam/action/list_reference_documents")
                .queryParam("examId", exam1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .get(JsonObject.class);
        Assert.assertEquals(2, t1RefList.getJsonArray("documents").size());

        JsonObject aliceTake = target().path("/plugin/online-exam/action/take_exam")
                .queryParam("examId", exam1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceToken)
                .get(JsonObject.class);
        JsonArray aliceRefDocs = aliceTake.getJsonObject("exam").getJsonArray("reference_documents");
        Assert.assertEquals(1, aliceRefDocs.size());
        Assert.assertEquals(sharedDocId, aliceRefDocs.getJsonObject(0).getString("id"));
        Assert.assertEquals("SecGuide Shared", aliceRefDocs.getJsonObject(0).getString("title"));
        JsonArray questions = aliceTake.getJsonObject("exam").getJsonArray("questions");
        Assert.assertFalse(questions.getJsonObject(2).containsKey("correct_boolean"));

        // Alice submits 2 right, 1 wrong -> 66.67 / 100.0; retake blocked
        JsonObject submit1 = Json.createObjectBuilder()
                .add("exam_id", exam1Id)
                .add("answers", Json.createObjectBuilder()
                        .add("q1", "c1")
                        .add("q2", Json.createArrayBuilder().add("c1").add("c2"))
                        .add("q3", "false"))
                .build();
        JsonObject res1 = target().path("/plugin/online-exam/action/submit_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceToken)
                .post(Entity.json(submit1.toString()), JsonObject.class);
        Assert.assertEquals(66.67, res1.getJsonObject("attempt").getJsonNumber("score").doubleValue(), 0.01);

        Response retakeBlocked = target().path("/plugin/online-exam/action/submit_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceToken)
                .post(Entity.json(submit1.toString()));
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), retakeBlocked.getStatus());

        // Teacher 2 grants bob on Exam 2 and bob submits 100.0
        target().path("/plugin/online-exam/action/set_exam_acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t2Token)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("exam_id", exam2Id).add("target_type", "GROUP").add("target_id", g2Id).add("allow", true).build().toString()), JsonObject.class);
        // Also grant bob directly
        String bobId = null;
        for (int i = 0; i < usersResp.getJsonArray("users").size(); i++) {
            JsonObject u = usersResp.getJsonArray("users").getJsonObject(i);
            if ("exam_bob".equals(u.getString("username"))) bobId = u.getString("id");
        }
        target().path("/plugin/online-exam/action/set_exam_acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t2Token)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("exam_id", exam2Id).add("target_type", "USER").add("target_id", bobId).add("allow", true).build().toString()), JsonObject.class);
        target().path("/plugin/online-exam/action/submit_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, bobToken)
                .post(Entity.json(Json.createObjectBuilder()
                        .add("exam_id", exam2Id)
                        .add("answers", Json.createObjectBuilder()
                                .add("q1", "c1")
                                .add("q2", Json.createArrayBuilder().add("c1").add("c2"))
                                .add("q3", "true"))
                        .build().toString()), JsonObject.class);

        // 5. Role-scoped attempts & localized Summary PDF export with date range
        String today = LocalDate.now().toString();
        String monthAgo = LocalDate.now().minusDays(30).toString();

        // Top Admin sees both SEC-101 (Online Exam 01) and LAW-201 (Online Exam 02)
        byte[] mgrSummaryZh = target().path("/plugin/online-exam/action/export_summary_pdf")
                .queryParam("from", monthAgo).queryParam("to", today).queryParam("lang", "zh_TW")
                .request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, mgrToken).get(byte[].class);
        try (PDDocument doc = PDDocument.load(mgrSummaryZh)) {
            String text = new PDFTextStripper().getText(doc);
            Assert.assertTrue(text.contains("線上考試成績總覽彙總表"));
            Assert.assertTrue(text.contains("SEC-101"));
            Assert.assertTrue(text.contains("LAW-201"));
            Assert.assertTrue(text.contains("OnlineExam01"));
            Assert.assertTrue(text.contains("OnlineExam02"));
        }

        // Teacher 1 summary PDF in English ONLY contains SEC-101, NOT LAW-201
        byte[] t1SummaryEn = target().path("/plugin/online-exam/action/export_summary_pdf")
                .queryParam("from", monthAgo).queryParam("to", today).queryParam("lang", "en")
                .request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token).get(byte[].class);
        try (PDDocument doc = PDDocument.load(t1SummaryEn)) {
            String text = new PDFTextStripper().getText(doc);
            Assert.assertTrue(text.contains("Online Exam Score Summary Report"));
            Assert.assertTrue(text.contains("SEC-101"));
            Assert.assertFalse(text.contains("LAW-201"));
        }

        // Examinee Alice summary PDF ONLY contains Alice's SEC-101 attempt
        byte[] aliceSummaryEn = target().path("/plugin/online-exam/action/export_summary_pdf")
                .queryParam("from", monthAgo).queryParam("to", today).queryParam("lang", "en")
                .request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceToken).get(byte[].class);
        try (PDDocument doc = PDDocument.load(aliceSummaryEn)) {
            String text = new PDFTextStripper().getText(doc);
            Assert.assertTrue(text.contains("exam_alice"));
            Assert.assertFalse(text.contains("exam_bob"));
        }

        // 6. Delete exam with attempts warning -> force delete preserves attempt & PDF export
        Response delWarn = target().path("/plugin/online-exam/action/delete_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(Json.createObjectBuilder().add("id", exam1Id).add("force", false).build().toString()));
        Assert.assertEquals(Response.Status.CONFLICT.getStatusCode(), delWarn.getStatus());

        target().path("/plugin/online-exam/action/delete_exam").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, t1Token)
                .post(Entity.json(Json.createObjectBuilder().add("id", exam1Id).add("force", true).build().toString()), JsonObject.class);

        byte[] attemptPdf = target().path("/plugin/online-exam/action/export_attempt_pdf")
                .queryParam("examId", exam1Id).queryParam("lang", "en")
                .request().cookie(TokenBasedSecurityFilter.COOKIE_NAME, aliceToken).get(byte[].class);
        try (PDDocument attemptDoc = PDDocument.load(attemptPdf)) {
            String attemptText = new PDFTextStripper().getText(attemptDoc);
            Assert.assertTrue(attemptText.contains("SEC-101"));
            Assert.assertTrue(attemptText.contains("exam_alice"));
        }
    }
}
