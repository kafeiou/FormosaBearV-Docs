package com.sismics.docs.core.util.plugin.exam;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.AuditLogType;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.GroupDao;
import com.sismics.docs.core.dao.PluginDataDao;
import com.sismics.docs.core.dao.PluginTagDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.criteria.GroupCriteria;
import com.sismics.docs.core.dao.criteria.UserCriteria;
import com.sismics.docs.core.dao.dto.GroupDto;
import com.sismics.docs.core.dao.dto.UserDto;
import com.sismics.docs.core.event.DocumentCreatedAsyncEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.Acl;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.Group;
import com.sismics.docs.core.model.jpa.PluginData;
import com.sismics.docs.core.model.jpa.PluginTag;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.AuditLogUtil;
import com.sismics.docs.core.util.FileUtil;
import com.sismics.docs.core.util.plugin.PluginContext;
import com.sismics.docs.core.util.plugin.PluginHandler;
import com.sismics.docs.core.util.plugin.PluginPackageUtil;
import com.sismics.docs.core.util.plugin.PluginResult;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.persistence.EntityManager;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Backend handler for the Online Exam plugin with three-tier roles (Top Admin > Subject Teacher > Examinee).
 */
public class ExamPluginHandler implements PluginHandler {
    public static final String PLUGIN_ID = "online-exam";
    private static final String COLLECTION_EXAMS = "exams";
    private static final String COLLECTION_ATTEMPTS = "attempts";
    private static final String COLLECTION_CONFIG = "config";
    private static final String KEY_TEACHERS = "teachers";
    private static final String KEY_DEMO_SEEDED = "demo_seeded";
    private static final double DEFAULT_TOTAL_SCORE = 100.0;
    private static final long MAX_DEMO_SEED_FILE_BYTES = 5L * 1024L * 1024L;

    private static final String DEMO_TEACHERS_GROUP = "ExamTeachers";
    private static final String DEMO_TEACHERS_GROUP_DESC = "Online Exam subject teachers (create and manage exams)";
    private static final String DEMO_STUDENTS_GROUP = "ExamStudents";
    private static final String DEMO_STUDENTS_GROUP_DESC = "Online Exam examinees (take assigned exams)";
    private static final String DEMO_ROOT_TAG_NAME = "OnlineExamMaterials";
    private static final String DEMO_CHILD_TAG_NAME = "SecurityFundamentals";
    private static final String DEMO_DOC_TITLE = "Security Fundamentals Guide";
    private static final String DEMO_EXAM_CODE = "SEC-001";
    private static final String DEMO_EXAM_TITLE = "Security Fundamentals Assessment";
    private static final String DEMO_DOC_DESCRIPTION =
            "<ul>"
                    + "<li><strong>[EN]</strong> Reference textbook for the Security Fundamentals Assessment (<code>SEC-001</code>). Includes PDF guides in all 16 supported languages.</li>"
                    + "<li><strong>[zh_TW]</strong> 「<code>SEC-001</code> 資安基礎測驗」參考教材文件，內含全部 16 種支援語系之 PDF 講義，可於應試時開啟對照。</li>"
                    + "<li><strong>[zh_CN]</strong> “<code>SEC-001</code> 资安基础测验”参考教材文件，内含全部 16 种支持语系的 PDF 讲义，可在考试时打开对照。</li>"
                    + "<li><strong>[ja]</strong> 「<code>SEC-001</code> 情報セキュリティ基礎試験」の参考教材ドキュメント（全16言語のPDFガイドを収録）。</li>"
                    + "<li><strong>[vi]</strong> Tài liệu giáo trình tham khảo cho bài kiểm tra <code>SEC-001</code> (bao gồm PDF bằng cả 16 ngôn ngữ được hỗ trợ).</li>"
                    + "<li><strong>[de]</strong> Referenzlehrbuch für die Prüfung <code>SEC-001</code> (enthält PDF-Leitfäden in allen 16 unterstützten Sprachen).</li>"
                    + "<li><strong>[fr]</strong> Manuel de référence pour l'évaluation <code>SEC-001</code> (inclut les guides PDF dans les 16 langues prises en charge).</li>"
                    + "<li><strong>[es]</strong> Manual de referencia para la evaluación <code>SEC-001</code> (incluye guías PDF en los 16 idiomas admitidos).</li>"
                    + "<li><strong>[pt]</strong> Manual de referência para a avaliação <code>SEC-001</code> (inclui guias em PDF nos 16 idiomas suportados).</li>"
                    + "<li><strong>[pt_BR]</strong> Manual de referência para a avaliação <code>SEC-001</code> (inclui guias em PDF nos 16 idiomas suportados).</li>"
                    + "<li><strong>[it]</strong> Manuale di riferimento per la valutazione <code>SEC-001</code> (include guide PDF in tutte le 16 lingue supportate).</li>"
                    + "<li><strong>[pl]</strong> Podręcznik referencyjny do egzaminu <code>SEC-001</code> (zawiera przewodniki PDF we wszystkich 16 obsługiwanych językach).</li>"
                    + "<li><strong>[ru]</strong> Справочное учебное пособие для экзамена <code>SEC-001</code> (включает PDF-руководства на всех 16 поддерживаемых языках).</li>"
                    + "<li><strong>[el]</strong> Εγχειρίδιο αναφοράς για την εξέταση <code>SEC-001</code> (περιλαμβάνει οδηγούς PDF και στις 16 υποστηριζόμενες γλώσσες).</li>"
                    + "<li><strong>[sq_AL]</strong> Libër referimi për provimin <code>SEC-001</code> (përfshin udhëzues PDF në të 16 gjuhët e mbështetura).</li>"
                    + "<li><strong>[sr]</strong> Референтни уџбеник за испит <code>SEC-001</code> (садржи PDF водиче на свих 16 подржаних језика).</li>"
                    + "</ul>";
    private static final String DEFAULT_DEMO_GIFT =
            "::Q1:: 根據《資安基礎講義》第一章，下列何者屬於強化帳號安全的最佳實務？ {\n"
                    + "  =啟用多因素驗證（MFA）並使用高強度密碼\n"
                    + "  ~所有系統共用同一組密碼方便記憶\n"
                    + "  ~將密碼寫在螢幕便利貼上\n"
                    + "  ~停用登入失敗鎖定機制\n"
                    + "}\n\n"
                    + "::Q2:: 根據《資安基礎講義》第二章，防範網路釣魚（Phishing）與社交工程時應採取哪些措施？（複選） {\n"
                    + "  ~%50%仔細檢查寄件者網域與連結網址是否偽造\n"
                    + "  ~%50%切勿隨意開啟來源不明的郵件附加檔案\n"
                    + "  ~%-50%收到要求立即提供密碼的緊急信件時直接回覆密碼\n"
                    + "  ~%-50%關閉防毒與端點防護軟體以加快收信速度\n"
                    + "}\n\n"
                    + "::Q3:: 根據《資安基礎講義》第三章，「3-2-1 備份原則」是指保存 3 份資料、使用 2 種不同媒介，且至少 1 份存放於異地或離線環境。 {TRUE}\n\n"
                    + "::Q4:: 根據《資安基礎講義》第四章，「最小權限原則（Least Privilege）」是指為了工作方便，預設應給予所有員工最高系統管理員（Admin）權限。 {FALSE}\n";

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public void onEnabled(String installerUserId) throws Exception {
        String actorId = Strings.isNullOrEmpty(installerUserId) ? "admin" : installerUserId;
        PluginDataDao dataDao = new PluginDataDao();
        PluginData seededFlag = dataDao.getByKey(PLUGIN_ID, COLLECTION_CONFIG, KEY_DEMO_SEEDED);
        if (seededFlag != null && "true".equalsIgnoreCase(seededFlag.getValue())) {
            GroupDao groupDao = new GroupDao();
            Group existingTeachers = groupDao.getActiveByName(DEMO_TEACHERS_GROUP);
            if (existingTeachers != null && Strings.isNullOrEmpty(existingTeachers.getDescription())) {
                existingTeachers.setDescription(DEMO_TEACHERS_GROUP_DESC);
                groupDao.update(existingTeachers, actorId);
            }
            Group existingStudents = groupDao.getActiveByName(DEMO_STUDENTS_GROUP);
            if (existingStudents != null && Strings.isNullOrEmpty(existingStudents.getDescription())) {
                existingStudents.setDescription(DEMO_STUDENTS_GROUP_DESC);
                groupDao.update(existingStudents, actorId);
            }
            EntityManager em = ThreadLocalContext.get().getEntityManager();
            List<Document> existing = em.createQuery(
                    "select d from Document d where d.title = :title and d.deleteDate is null",
                    Document.class)
                    .setParameter("title", DEMO_DOC_TITLE)
                    .setMaxResults(1)
                    .getResultList();
            if (!existing.isEmpty()) {
                Document doc = existing.get(0);
                if (!DEMO_DOC_DESCRIPTION.equals(doc.getDescription())) {
                    doc.setDescription(DEMO_DOC_DESCRIPTION);
                    new DocumentDao().update(doc, actorId);
                }
            }
            return;
        }

        // 1. Groups (No Privilege Escalation: roleId is strictly null, existing groups are not modified)
        GroupDao groupDao = new GroupDao();
        Group teachersGroup = groupDao.getActiveByName(DEMO_TEACHERS_GROUP);
        String teachersGroupId;
        if (teachersGroup == null) {
            Group g = new Group();
            g.setName(DEMO_TEACHERS_GROUP);
            g.setDescription(DEMO_TEACHERS_GROUP_DESC);
            g.setRoleId(null);
            teachersGroupId = groupDao.create(g, actorId);
        } else {
            if (Strings.isNullOrEmpty(teachersGroup.getDescription())) {
                teachersGroup.setDescription(DEMO_TEACHERS_GROUP_DESC);
                groupDao.update(teachersGroup, actorId);
            }
            teachersGroupId = teachersGroup.getId();
        }

        Group studentsGroup = groupDao.getActiveByName(DEMO_STUDENTS_GROUP);
        String studentsGroupId;
        if (studentsGroup == null) {
            Group g = new Group();
            g.setName(DEMO_STUDENTS_GROUP);
            g.setDescription(DEMO_STUDENTS_GROUP_DESC);
            g.setRoleId(null);
            studentsGroupId = groupDao.create(g, actorId);
        } else {
            if (Strings.isNullOrEmpty(studentsGroup.getDescription())) {
                studentsGroup.setDescription(DEMO_STUDENTS_GROUP_DESC);
                groupDao.update(studentsGroup, actorId);
            }
            studentsGroupId = studentsGroup.getId();
        }

        // Authorize ExamTeachers group as subject teachers in plugin config
        JsonObject teachersCfg = loadTeachersConfig();
        Set<String> teacherUsers = jsonStringSet(teachersCfg, "users");
        Set<String> teacherGroups = jsonStringSet(teachersCfg, "groups");
        teacherGroups.add(teachersGroupId);
        JsonObject updatedTeachersCfg = Json.createObjectBuilder()
                .add("users", toJsonArray(teacherUsers))
                .add("groups", toJsonArray(teacherGroups))
                .build();
        dataDao.upsert(PLUGIN_ID, COLLECTION_CONFIG, KEY_TEACHERS, actorId, updatedTeachersCfg.toString());

        // 2. Hierarchical Tags ("線上考試教材" -> "資安基礎教材") with Least Privilege ACLs
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        String rootTagId = findOrCreateDemoTag(em, DEMO_ROOT_TAG_NAME, null, "#2563eb", actorId, teachersGroupId, studentsGroupId);
        String childTagId = findOrCreateDemoTag(em, DEMO_CHILD_TAG_NAME, rootTagId, "#0ea5e9", actorId, teachersGroupId, studentsGroupId);

        // 3. Demo Textbook Document + PDF File ("資安基礎教材（範例教科書）" + "資安基礎講義.pdf")
        String demoDocId = findOrCreateDemoTextbookDocument(em, actorId, childTagId, teachersGroupId, studentsGroupId);

        // 4. Demo Exam ("SEC-DEMO-101" bound to demoDocId)
        createDemoExamIfAbsent(dataDao, actorId, teachersGroupId, studentsGroupId, demoDocId);

        dataDao.upsert(PLUGIN_ID, COLLECTION_CONFIG, KEY_DEMO_SEEDED, actorId, "true");
    }

    private String findOrCreateDemoTag(EntityManager em, String name, String parentId, String color,
                                       String actorId, String teachersGroupId, String studentsGroupId) {
        List<Tag> existing = em.createQuery(
                "select t from Tag t where t.name = :name and t.deleteDate is null",
                Tag.class)
                .setParameter("name", name)
                .getResultList();
        for (Tag t : existing) {
            if ((parentId == null && t.getParentId() == null) || (parentId != null && parentId.equals(t.getParentId()))) {
                return t.getId();
            }
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setParentId(parentId);
        tag.setColor(color);
        tag.setUserId(actorId);
        tag.setCreateDate(new Date());
        tag.setInheritAcl(true);
        tag.setHidden(false);
        String tagId = new TagDao().create(tag, actorId);

        grantTargetAclIfAbsent(tagId, actorId, PermType.READ, actorId);
        grantTargetAclIfAbsent(tagId, actorId, PermType.WRITE, actorId);
        grantTargetAclIfAbsent(tagId, teachersGroupId, PermType.READ, actorId);
        grantTargetAclIfAbsent(tagId, teachersGroupId, PermType.WRITE, actorId);
        grantTargetAclIfAbsent(tagId, studentsGroupId, PermType.READ, actorId);
        return tagId;
    }

    private String findOrCreateDemoTextbookDocument(EntityManager em, String actorId, String tagId,
                                                    String teachersGroupId, String studentsGroupId) throws Exception {
        List<Document> existing = em.createQuery(
                "select d from Document d where d.title = :title and d.deleteDate is null",
                Document.class)
                .setParameter("title", DEMO_DOC_TITLE)
                .setMaxResults(1)
                .getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }

        Document doc = new Document();
        doc.setUserId(actorId);
        doc.setTitle(DEMO_DOC_TITLE);
        doc.setDescription(DEMO_DOC_DESCRIPTION);
        doc.setLanguage("eng");
        doc.setCreateDate(new Date());
        String docId = new DocumentDao().create(doc, actorId);

        grantTargetAclIfAbsent(docId, actorId, PermType.READ, actorId);
        grantTargetAclIfAbsent(docId, actorId, PermType.WRITE, actorId);
        grantTargetAclIfAbsent(docId, teachersGroupId, PermType.READ, actorId);
        grantTargetAclIfAbsent(docId, teachersGroupId, PermType.WRITE, actorId);
        grantTargetAclIfAbsent(docId, studentsGroupId, PermType.READ, actorId);

        new TagDao().updateTagList(docId, Set.of(tagId));

        DocumentCreatedAsyncEvent event = new DocumentCreatedAsyncEvent();
        event.setUserId(actorId);
        event.setDocumentId(docId);
        ThreadLocalContext.get().addAsyncEvent(event);

        // Generate & attach all 16 localized textbook PDFs using FileService temporary files so async text extraction & Lucene indexing succeed
        for (String localeCode : ExamPdfExporter.SUPPORTED_TEXTBOOK_LOCALES) {
            String pdfFileName = ExamPdfExporter.getTextbookFileName(localeCode);
            byte[] pdfBytes = ExamPdfExporter.exportTextbookPdf(localeCode);
            if (pdfBytes.length > 0 && pdfBytes.length <= MAX_DEMO_SEED_FILE_BYTES) {
                Path tempPdf = AppContext.getInstance().getFileService().createTemporaryFile(pdfFileName);
                Files.write(tempPdf, pdfBytes);
                FileUtil.createFile(pdfFileName, null, tempPdf, pdfBytes.length, "eng", actorId, docId);
            }
        }
        return docId;
    }

    private void createDemoExamIfAbsent(PluginDataDao dataDao, String actorId,
                                        String teachersGroupId, String studentsGroupId, String demoDocId) throws Exception {
        for (PluginData row : dataDao.findByCollection(PLUGIN_ID, COLLECTION_EXAMS)) {
            JsonObject ex = parseJson(row.getValue());
            if (DEMO_EXAM_CODE.equals(ex.getString("exam_code", ""))) {
                return;
            }
        }

        String giftText = DEFAULT_DEMO_GIFT;
        Path giftAsset = PluginPackageUtil.resolvePluginAsset(PLUGIN_ID, "seed/SEC-001.gift");
        if (giftAsset != null && Files.size(giftAsset) <= 256 * 1024L) {
            String fileContent = Files.readString(giftAsset, StandardCharsets.UTF_8);
            if (!fileContent.trim().isEmpty()) {
                giftText = fileContent;
            }
        }

        List<GiftParser.GiftQuestion> parsed = GiftParser.parse(giftText);
        JsonArray questionsArr = GiftParser.toJsonArray(parsed, true);

        JsonObject i18nObj = null;
        Path i18nAsset = PluginPackageUtil.resolvePluginAsset(PLUGIN_ID, "seed/SEC-001-i18n.json");
        if (i18nAsset != null && Files.size(i18nAsset) <= 512 * 1024L) {
            i18nObj = parseJson(Files.readString(i18nAsset, StandardCharsets.UTF_8));
        }

        User actorUser = new UserDao().getById(actorId);
        String actorUsername = actorUser != null ? actorUser.getUsername() : "admin";
        String ownerLabel = actorUsername + " (" + DEMO_TEACHERS_GROUP + ")";
        String examId = UUID.randomUUID().toString();

        JsonObjectBuilder examBuilder = Json.createObjectBuilder()
                .add("id", examId)
                .add("title", DEMO_EXAM_TITLE)
                .add("exam_code", DEMO_EXAM_CODE)
                .add("exam_version", "1.0")
                .add("owner_user_id", actorId)
                .add("owner_username", actorUsername)
                .add("owner_display_name", actorUsername)
                .add("owner_group_id", teachersGroupId)
                .add("owner_group_name", DEMO_TEACHERS_GROUP)
                .add("owner_label", ownerLabel)
                .add("allowed_users", Json.createArrayBuilder().add(actorId))
                .add("allowed_groups", Json.createArrayBuilder().add(studentsGroupId))
                .add("reference_document_ids", Json.createArrayBuilder().add(demoDocId))
                .add("retake_allowed", true)
                .add("enabled", true)
                .add("total_score", DEFAULT_TOTAL_SCORE)
                .add("question_count", questionsArr.size())
                .add("updated_at", System.currentTimeMillis())
                .add("questions", questionsArr);
        if (i18nObj != null && !i18nObj.isEmpty()) {
            examBuilder.add("i18n", i18nObj);
        }

        dataDao.upsert(PLUGIN_ID, COLLECTION_EXAMS, examId, actorId, examBuilder.build().toString());
    }

    private void grantTargetAclIfAbsent(String sourceId, String targetId, PermType perm, String actorId) {
        if (Strings.isNullOrEmpty(sourceId) || Strings.isNullOrEmpty(targetId)) {
            return;
        }
        AclDao aclDao = new AclDao();
        if (!aclDao.hasDirectAcl(sourceId, perm, targetId, AclType.USER)) {
            Acl acl = new Acl();
            acl.setSourceId(sourceId);
            acl.setTargetId(targetId);
            acl.setPerm(perm);
            acl.setType(AclType.USER);
            aclDao.create(acl, actorId);
        }
    }

    @Override
    public Optional<String> findTagReferenceReason(String tagId) {
        if (Strings.isNullOrEmpty(tagId)) {
            return Optional.empty();
        }
        List<PluginData> examRows = new PluginDataDao().findByCollection(PLUGIN_ID, COLLECTION_EXAMS);
        for (PluginData row : examRows) {
            JsonObject exam = parseJson(row.getValue());
            if (tagId.equals(exam.getString("tag_id", ""))) {
                return Optional.of("Exam \"" + exam.getString("title", exam.getString("exam_code", row.getKey())) + "\" references this tag");
            }
        }
        return Optional.empty();
    }

    @Override
    public PluginResult handleAction(String method, String action, PluginContext context) throws Exception {
        boolean isPost = "POST".equalsIgnoreCase(method);
        switch (action) {
            case "info":
                return handleInfo(context);
            case "list_teachers":
                return handleListTeachers(context);
            case "set_teacher":
                return isPost ? handleSetTeacher(context) : error(405, "MethodNotAllowed", "POST required");
            case "parse_gift":
                return isPost ? handleParseGift(context) : error(405, "MethodNotAllowed", "POST required");
            case "save_exam":
                return isPost ? handleSaveExam(context) : error(405, "MethodNotAllowed", "POST required");
            case "toggle_exam":
                return isPost ? handleToggleExam(context) : error(405, "MethodNotAllowed", "POST required");
            case "delete_exam":
                return isPost ? handleDeleteExam(context) : error(405, "MethodNotAllowed", "POST required");
            case "exams":
                return handleListExams(context);
            case "take_exam":
                return handleTakeExam(context);
            case "submit_exam":
                return isPost ? handleSubmitExam(context) : error(405, "MethodNotAllowed", "POST required");
            case "attempts":
                return handleListAttempts(context);
            case "list_acl_targets":
                return handleListAclTargets(context);
            case "set_exam_acl":
                return isPost ? handleSetExamAcl(context) : error(405, "MethodNotAllowed", "POST required");
            case "list_reference_documents":
                return handleListReferenceDocuments(context);
            case "set_exam_reference_documents":
                return isPost ? handleSetExamReferenceDocuments(context) : error(405, "MethodNotAllowed", "POST required");
            case "export_blank_pdf":
                return handleExportBlankPdf(context);
            case "export_attempt_pdf":
                return handleExportAttemptPdf(context);
            case "export_summary_pdf":
                return handleExportSummaryPdf(context);
            default:
                return error(404, "UnknownAction", "Unknown exam action: " + action);
        }
    }

    private PluginResult handleInfo(PluginContext ctx) {
        boolean topAdmin = isTopAdmin(ctx);
        JsonObject teachersCfg = loadTeachersConfig();
        boolean teacher = topAdmin || isTeacher(ctx, teachersCfg);

        Set<String> teacherGroupIds = jsonStringSet(teachersCfg, "groups");
        JsonArrayBuilder userTeacherGroups = Json.createArrayBuilder();
        for (GroupDto g : new GroupDao().findByCriteria(new GroupCriteria(), null)) {
            if (teacherGroupIds.contains(g.getId()) && (topAdmin || ctx.getTargetIdList().contains(g.getId()))) {
                userTeacherGroups.add(Json.createObjectBuilder()
                        .add("id", g.getId())
                        .add("name", g.getName())
                        .add("description", Strings.nullToEmpty(g.getDescription())));
            }
        }

        return PluginResult.ok(Json.createObjectBuilder()
                .add("is_top_admin", topAdmin)
                .add("is_teacher", teacher)
                .add("is_manager", teacher)
                .add("user_id", ctx.getUserId())
                .add("username", ctx.getUsername())
                .add("display_name", Strings.isNullOrEmpty(ctx.getUserDisplayName()) ? ctx.getUsername() : ctx.getUserDisplayName())
                .add("teacher_groups", userTeacherGroups)
                .build());
    }

    private PluginResult handleListTeachers(PluginContext ctx) {
        if (!isTopAdmin(ctx)) {
            return error(403, "ForbiddenError", "Top admin permission required");
        }
        JsonObject teachersCfg = loadTeachersConfig();
        Set<String> teacherUsers = jsonStringSet(teachersCfg, "users");
        Set<String> teacherGroups = jsonStringSet(teachersCfg, "groups");

        JsonArrayBuilder usersArr = Json.createArrayBuilder();
        for (UserDto u : new UserDao().findByCriteria(new UserCriteria(), null)) {
            if (u.getDisableTimestamp() != null) {
                continue;
            }
            usersArr.add(Json.createObjectBuilder()
                    .add("id", u.getId())
                    .add("username", u.getUsername())
                    .add("name", Strings.nullToEmpty(u.getName()))
                    .add("allowed", teacherUsers.contains(u.getId())));
        }

        JsonArrayBuilder groupsArr = Json.createArrayBuilder();
        for (GroupDto g : new GroupDao().findByCriteria(new GroupCriteria(), null)) {
            groupsArr.add(Json.createObjectBuilder()
                    .add("id", g.getId())
                    .add("name", g.getName())
                    .add("description", Strings.nullToEmpty(g.getDescription()))
                    .add("allowed", teacherGroups.contains(g.getId())));
        }

        return PluginResult.ok(Json.createObjectBuilder()
                .add("users", usersArr)
                .add("groups", groupsArr)
                .build());
    }

    private PluginResult handleSetTeacher(PluginContext ctx) {
        if (!isTopAdmin(ctx)) {
            return error(403, "ForbiddenError", "Top admin permission required");
        }
        JsonObject body = parseJson(ctx.getRequestBody());
        String targetType = body.getString("target_type", "").trim().toUpperCase(Locale.ROOT);
        String targetId = body.getString("target_id", "").trim();
        boolean allow = body.getBoolean("allow", true);

        String targetLabel;
        if ("USER".equals(targetType)) {
            User targetUser = new UserDao().getById(targetId);
            if (targetUser == null || targetUser.getDeleteDate() != null || targetUser.getDisableDate() != null) {
                return error(400, "InvalidTarget", "Target user does not exist or is disabled");
            }
            targetLabel = targetUser.getUsername();
        } else if ("GROUP".equals(targetType)) {
            Group targetGroup = new GroupDao().getActiveById(targetId);
            if (targetGroup == null || targetGroup.getDeleteDate() != null) {
                return error(400, "InvalidTarget", "Target group does not exist or is deleted");
            }
            targetLabel = targetGroup.getName();
        } else {
            return error(400, "ValidationError", "target_type must be USER or GROUP");
        }

        JsonObject cfg = loadTeachersConfig();
        Set<String> users = jsonStringSet(cfg, "users");
        Set<String> groups = jsonStringSet(cfg, "groups");
        Set<String> targetSet = "USER".equals(targetType) ? users : groups;
        if (allow) {
            targetSet.add(targetId);
        } else {
            targetSet.remove(targetId);
        }

        JsonObject updated = Json.createObjectBuilder()
                .add("users", toJsonArray(users))
                .add("groups", toJsonArray(groups))
                .build();
        new PluginDataDao().upsert(PLUGIN_ID, COLLECTION_CONFIG, KEY_TEACHERS, ctx.getUserId(), updated.toString());
        auditExamAction(ctx, AuditLogType.UPDATE, "Teacher role " + (allow ? "granted" : "revoked") + " for " + targetType + " " + targetLabel);

        return PluginResult.ok(Json.createObjectBuilder().add("status", "ok").build());
    }

    private PluginResult handleParseGift(PluginContext ctx) {
        if (!isTeacher(ctx, loadTeachersConfig()) && !isTopAdmin(ctx)) {
            return error(403, "ForbiddenError", "Teacher or admin permission required");
        }
        JsonObject body = parseJson(ctx.getRequestBody());
        String gift = body.getString("gift", "");
        try {
            List<GiftParser.GiftQuestion> questions = GiftParser.parse(gift);
            return PluginResult.ok(Json.createObjectBuilder()
                    .add("count", questions.size())
                    .add("questions", GiftParser.toJsonArray(questions, true))
                    .build());
        } catch (IllegalArgumentException e) {
            return error(400, "InvalidGiftFormat", e.getMessage());
        }
    }

    private PluginResult handleSaveExam(PluginContext ctx) {
        JsonObject teachersCfg = loadTeachersConfig();
        boolean topAdmin = isTopAdmin(ctx);
        if (!topAdmin && !isTeacher(ctx, teachersCfg)) {
            return error(403, "ForbiddenError", "Teacher or admin permission required");
        }

        JsonObject body = parseJson(ctx.getRequestBody());
        String examId = body.getString("id", "").trim();
        boolean isNew = examId.isEmpty();
        JsonObject existingExam = isNew ? null : loadExam(examId);
        if (!isNew && existingExam != null && !canManageExam(ctx, existingExam)) {
            return error(403, "ForbiddenError", "Cannot modify another teacher's exam");
        }
        if (isNew) {
            examId = UUID.randomUUID().toString();
        }

        String title = body.getString("title", "").trim();
        String examCode = body.getString("exam_code", "").trim();
        String examVersion = body.getString("exam_version", "").trim();
        boolean retakeAllowed = body.getBoolean("retake_allowed", false);
        boolean enabled = body.getBoolean("enabled", true);
        double totalScore = body.containsKey("total_score")
                ? Math.max(1.0, Math.min(10000.0, body.getJsonNumber("total_score").doubleValue()))
                : DEFAULT_TOTAL_SCORE;
        String gift = body.getString("gift", "");

        if (title.isEmpty() || examCode.isEmpty() || examVersion.isEmpty()) {
            return error(400, "ValidationError", "請填寫考卷名稱、考卷代號與考卷版號");
        }

        JsonArray questionsArr;
        if (!gift.trim().isEmpty()) {
            try {
                List<GiftParser.GiftQuestion> parsed = GiftParser.parse(gift);
                questionsArr = GiftParser.toJsonArray(parsed, true);
            } catch (IllegalArgumentException e) {
                return error(400, "InvalidGiftFormat", e.getMessage());
            }
        } else if (body.containsKey("questions") && body.get("questions").getValueType() == JsonValue.ValueType.ARRAY) {
            questionsArr = body.getJsonArray("questions");
            if (questionsArr.isEmpty() || questionsArr.size() > GiftParser.MAX_QUESTIONS) {
                return error(400, "ValidationError", "考卷題數須介於 1 至 " + GiftParser.MAX_QUESTIONS + " 題之間");
            }
        } else {
            return error(400, "ValidationError", "請輸入 GIFT 題目內容");
        }

        // Determine owner & teacher group identity (e.g., "teacher01 (Online Exam 01)")
        String ownerUserId = existingExam != null ? existingExam.getString("owner_user_id", ctx.getUserId()) : ctx.getUserId();
        String ownerUsername = existingExam != null ? existingExam.getString("owner_username", ctx.getUsername()) : ctx.getUsername();
        String displayName = Strings.isNullOrEmpty(ctx.getUserDisplayName()) ? ctx.getUsername() : ctx.getUserDisplayName();
        String ownerDisplayName = existingExam != null ? existingExam.getString("owner_display_name", displayName) : displayName;

        Set<String> teacherGroupIds = jsonStringSet(teachersCfg, "groups");
        String requestedGroupId = body.getString("owner_group_id", "").trim();
        String ownerGroupId = "";
        String ownerGroupName = "";
        if (!requestedGroupId.isEmpty()) {
            if (!teacherGroupIds.contains(requestedGroupId) || (!topAdmin && !ctx.getTargetIdList().contains(requestedGroupId))) {
                return error(403, "ForbiddenError", "Invalid teacher group selection");
            }
            Group g = new GroupDao().getActiveById(requestedGroupId);
            if (g != null) {
                ownerGroupId = g.getId();
                ownerGroupName = g.getName();
            }
        } else if (existingExam != null && !existingExam.getString("owner_group_id", "").isEmpty()) {
            ownerGroupId = existingExam.getString("owner_group_id", "");
            ownerGroupName = existingExam.getString("owner_group_name", "");
        } else {
            // Auto-select if user belongs to a teacher group
            for (GroupDto g : new GroupDao().findByCriteria(new GroupCriteria(), null)) {
                if (teacherGroupIds.contains(g.getId()) && ctx.getTargetIdList().contains(g.getId())) {
                    ownerGroupId = g.getId();
                    ownerGroupName = g.getName();
                    break;
                }
            }
        }

        String ownerLabel = !ownerGroupName.isEmpty()
                ? ownerDisplayName + " (" + ownerGroupName + ")"
                : ownerDisplayName + " (" + ownerUsername + ")";

        JsonArray allowedUsers = existingExam != null && existingExam.containsKey("allowed_users")
                ? existingExam.getJsonArray("allowed_users")
                : Json.createArrayBuilder().build();
        JsonArray allowedGroups = existingExam != null && existingExam.containsKey("allowed_groups")
                ? existingExam.getJsonArray("allowed_groups")
                : Json.createArrayBuilder().build();
        String legacyTagId = existingExam != null ? existingExam.getString("tag_id", "") : body.getString("tag_id", "").trim();

        Set<String> refDocIds = new LinkedHashSet<>();
        if (body.containsKey("reference_document_ids") && body.get("reference_document_ids").getValueType() == JsonValue.ValueType.ARRAY) {
            AclDao aclDao = new AclDao();
            DocumentDao docDao = new DocumentDao();
            Set<String> existingRefIds = existingExam != null ? jsonStringSet(existingExam, "reference_document_ids") : Set.of();
            JsonArray reqDocs = body.getJsonArray("reference_document_ids");
            for (int i = 0; i < reqDocs.size(); i++) {
                String docId = reqDocs.getString(i, "").trim();
                if (docId.isEmpty()) {
                    continue;
                }
                if (!existingRefIds.contains(docId)) {
                    if (docDao.getById(docId) == null || !aclDao.checkPermission(docId, PermType.READ, ctx.getTargetIdList())) {
                        return error(403, "ForbiddenError", "No READ permission on reference document: " + docId);
                    }
                }
                refDocIds.add(docId);
            }
        } else if (existingExam != null && existingExam.containsKey("reference_document_ids")) {
            refDocIds.addAll(jsonStringSet(existingExam, "reference_document_ids"));
        }

        long now = System.currentTimeMillis();
        JsonObjectBuilder examBuilder = Json.createObjectBuilder()
                .add("id", examId)
                .add("title", title)
                .add("exam_code", examCode)
                .add("exam_version", examVersion)
                .add("owner_user_id", ownerUserId)
                .add("owner_username", ownerUsername)
                .add("owner_display_name", ownerDisplayName)
                .add("owner_group_id", ownerGroupId)
                .add("owner_group_name", ownerGroupName)
                .add("owner_label", ownerLabel)
                .add("allowed_users", allowedUsers)
                .add("allowed_groups", allowedGroups)
                .add("reference_document_ids", toJsonArray(refDocIds))
                .add("retake_allowed", retakeAllowed)
                .add("enabled", enabled)
                .add("total_score", totalScore)
                .add("question_count", questionsArr.size())
                .add("updated_at", now)
                .add("questions", questionsArr);
        if (!legacyTagId.isEmpty()) {
            examBuilder.add("tag_id", legacyTagId);
        }
        JsonObject examObj = examBuilder.build();

        new PluginDataDao().upsert(PLUGIN_ID, COLLECTION_EXAMS, examId, ctx.getUserId(), examObj.toString());
        auditExamAction(ctx, isNew ? AuditLogType.CREATE : AuditLogType.UPDATE,
                "Exam saved: " + examCode + " v" + examVersion + " (" + title + ") by " + ownerLabel);

        return PluginResult.ok(Json.createObjectBuilder()
                .add("exam", buildExamSummary(examObj, null, countAttemptsForExam(examId), true, canTakeExam(ctx, examObj), ctx.getTargetIdList()))
                .build());
    }

    private PluginResult handleToggleExam(PluginContext ctx) {
        JsonObject body = parseJson(ctx.getRequestBody());
        String examId = body.getString("id", "").trim();
        JsonObject exam = loadExam(examId);
        if (exam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        if (!canManageExam(ctx, exam)) {
            return error(403, "ForbiddenError", "Permission denied");
        }
        boolean nextEnabled = body.containsKey("enabled") ? body.getBoolean("enabled") : !exam.getBoolean("enabled", true);

        JsonObjectBuilder updated = Json.createObjectBuilder();
        for (String k : exam.keySet()) {
            if (!"enabled".equals(k) && !"updated_at".equals(k)) {
                updated.add(k, exam.get(k));
            }
        }
        updated.add("enabled", nextEnabled);
        updated.add("updated_at", System.currentTimeMillis());
        JsonObject savedObj = updated.build();
        new PluginDataDao().upsert(PLUGIN_ID, COLLECTION_EXAMS, examId, ctx.getUserId(), savedObj.toString());
        auditExamAction(ctx, AuditLogType.UPDATE,
                "Exam " + (nextEnabled ? "enabled" : "disabled") + ": " + exam.getString("exam_code", examId));

        return PluginResult.ok(Json.createObjectBuilder()
                .add("exam", buildExamSummary(savedObj, null, countAttemptsForExam(examId), true, canTakeExam(ctx, savedObj), ctx.getTargetIdList()))
                .build());
    }

    private PluginResult handleDeleteExam(PluginContext ctx) {
        JsonObject body = parseJson(ctx.getRequestBody());
        String examId = body.getString("id", "").trim();
        boolean force = body.getBoolean("force", false);
        if (examId.isEmpty()) {
            return error(400, "ValidationError", "id is required");
        }
        JsonObject exam = loadExam(examId);
        if (exam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        if (!canManageExam(ctx, exam)) {
            return error(403, "ForbiddenError", "Permission denied");
        }

        int attemptCount = countAttemptsForExam(examId);
        if (attemptCount > 0 && !force) {
            return PluginResult.error(409, Json.createObjectBuilder()
                    .add("type", "ExamHasAttempts")
                    .add("attempt_count", attemptCount)
                    .add("message", "已有 " + attemptCount + " 位考生完成此考卷；刪除考卷將保留歷史成績紀錄，確定要刪除嗎？")
                    .build());
        }

        new PluginDataDao().deleteByKey(PLUGIN_ID, COLLECTION_EXAMS, examId);
        auditExamAction(ctx, AuditLogType.DELETE,
                "Exam deleted (attempts preserved=" + attemptCount + "): " + exam.getString("exam_code", examId));

        return PluginResult.ok(Json.createObjectBuilder()
                .add("status", "ok")
                .add("preserved_attempts", attemptCount)
                .build());
    }

    private PluginResult handleListExams(PluginContext ctx) {
        String lang = ctx.getQueryParams().getOrDefault("lang", "en").trim();
        boolean topAdmin = isTopAdmin(ctx);
        boolean teacher = topAdmin || isTeacher(ctx, loadTeachersConfig());
        PluginDataDao dataDao = new PluginDataDao();
        List<PluginData> examRows = dataDao.findByCollection(PLUGIN_ID, COLLECTION_EXAMS);
        Map<String, Integer> attemptCounts = teacher ? countAllAttemptsByExam() : Map.of();

        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (PluginData row : examRows) {
            JsonObject exam = localizeExam(parseJson(row.getValue()), lang);
            boolean manageable = canManageExam(ctx, exam);
            boolean takeable = exam.getBoolean("enabled", true) && canTakeExam(ctx, exam);
            String examId = exam.getString("id");
            PluginData myAttemptRow = dataDao.getByKey(PLUGIN_ID, COLLECTION_ATTEMPTS, examId + ":" + ctx.getUserId());
            JsonObject myAttempt = myAttemptRow != null ? parseJson(myAttemptRow.getValue()) : null;

            if (!manageable && !takeable && myAttempt == null) {
                continue;
            }
            arr.add(buildExamSummary(exam, myAttempt, attemptCounts.getOrDefault(examId, 0), manageable, takeable, ctx.getTargetIdList()));
        }

        return PluginResult.ok(Json.createObjectBuilder()
                .add("is_top_admin", topAdmin)
                .add("is_teacher", teacher)
                .add("is_manager", teacher)
                .add("exams", arr)
                .build());
    }

    private PluginResult handleTakeExam(PluginContext ctx) {
        String examId = ctx.getQueryParams().getOrDefault("examId", "").trim();
        String lang = ctx.getQueryParams().getOrDefault("lang", "en").trim();
        JsonObject rawExam = loadExam(examId);
        if (rawExam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        JsonObject exam = localizeExam(rawExam, lang);
        boolean manageable = canManageExam(ctx, exam);
        boolean takeable = canTakeExam(ctx, exam);
        PluginData myAttemptRow = new PluginDataDao().getByKey(PLUGIN_ID, COLLECTION_ATTEMPTS, examId + ":" + ctx.getUserId());
        if (!manageable && !takeable && myAttemptRow == null) {
            return error(403, "ForbiddenError", "No permission for this exam");
        }
        if (!exam.getBoolean("enabled", true) && !manageable && myAttemptRow == null) {
            return error(403, "ExamDisabled", "This exam is currently disabled");
        }
        JsonObjectBuilder resp = Json.createObjectBuilder()
                .add("exam", stripAnswersFromExam(exam, ctx.getTargetIdList()));
        if (myAttemptRow != null) {
            resp.add("my_attempt", parseJson(myAttemptRow.getValue()));
        }
        return PluginResult.ok(resp.build());
    }

    private PluginResult handleSubmitExam(PluginContext ctx) {
        JsonObject body = parseJson(ctx.getRequestBody());
        String examId = body.getString("exam_id", "").trim();
        JsonObject exam = loadExam(examId);
        if (exam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        if (!canTakeExam(ctx, exam) && !canManageExam(ctx, exam)) {
            return error(403, "ForbiddenError", "No permission for this exam");
        }
        if (!exam.getBoolean("enabled", true)) {
            return error(400, "ExamDisabled", "This exam is currently disabled");
        }

        PluginDataDao dataDao = new PluginDataDao();
        String attemptKey = examId + ":" + ctx.getUserId();
        boolean retakeAllowed = exam.getBoolean("retake_allowed", false);
        if (!retakeAllowed && dataDao.getByKey(PLUGIN_ID, COLLECTION_ATTEMPTS, attemptKey) != null) {
            return error(400, "RetakeNotAllowed", "Retake is not allowed for this exam");
        }

        JsonObject rawAnswers = body.containsKey("answers") && body.get("answers").getValueType() == JsonValue.ValueType.OBJECT
                ? body.getJsonObject("answers")
                : Json.createObjectBuilder().build();

        JsonArray questions = exam.getJsonArray("questions");
        double totalScoreConfig = exam.containsKey("total_score")
                ? exam.getJsonNumber("total_score").doubleValue()
                : DEFAULT_TOTAL_SCORE;
        double perQuestionPoints = questions.isEmpty() ? 0.0 : totalScoreConfig / questions.size();

        JsonObjectBuilder sanitizedAnswersBuilder = Json.createObjectBuilder();
        JsonObjectBuilder resultsBuilder = Json.createObjectBuilder();
        double totalEarnedUnits = 0.0;

        for (int i = 0; i < questions.size(); i++) {
            JsonObject q = questions.getJsonObject(i);
            String qId = q.getString("id");
            String type = q.getString("type");
            double qEarned = 0.0;

            if (GiftParser.TYPE_TRUE_FALSE.equals(type)) {
                boolean expected = q.getBoolean("correct_boolean", false);
                if (rawAnswers.containsKey(qId)) {
                    JsonValue ansVal = rawAnswers.get(qId);
                    Boolean actual = null;
                    if (ansVal.getValueType() == JsonValue.ValueType.TRUE) {
                        actual = Boolean.TRUE;
                    } else if (ansVal.getValueType() == JsonValue.ValueType.FALSE) {
                        actual = Boolean.FALSE;
                    } else if (ansVal.getValueType() == JsonValue.ValueType.STRING) {
                        String s = rawAnswers.getString(qId).trim().toLowerCase(Locale.ROOT);
                        if ("true".equals(s)) {
                            actual = Boolean.TRUE;
                        } else if ("false".equals(s)) {
                            actual = Boolean.FALSE;
                        }
                    }
                    if (actual != null) {
                        sanitizedAnswersBuilder.add(qId, actual.toString());
                        if (actual.booleanValue() == expected) {
                            qEarned = 1.0;
                        }
                    }
                }
            } else if (GiftParser.TYPE_SINGLE_CHOICE.equals(type)) {
                String selectedId = "";
                if (rawAnswers.containsKey(qId) && rawAnswers.get(qId).getValueType() == JsonValue.ValueType.STRING) {
                    selectedId = rawAnswers.getString(qId);
                    sanitizedAnswersBuilder.add(qId, selectedId);
                }
                JsonArray choices = q.getJsonArray("choices");
                for (int c = 0; c < choices.size(); c++) {
                    JsonObject choice = choices.getJsonObject(c);
                    if (choice.getString("id").equals(selectedId) && choice.getJsonNumber("weight").doubleValue() > 0.0) {
                        qEarned = 1.0;
                        break;
                    }
                }
            } else if (GiftParser.TYPE_MULTIPLE_CHOICE.equals(type)) {
                Set<String> selectedIds = new HashSet<>();
                JsonArrayBuilder selArr = Json.createArrayBuilder();
                if (rawAnswers.containsKey(qId) && rawAnswers.get(qId).getValueType() == JsonValue.ValueType.ARRAY) {
                    JsonArray arr = rawAnswers.getJsonArray(qId);
                    for (int k = 0; k < arr.size() && k < 50; k++) {
                        if (arr.get(k).getValueType() == JsonValue.ValueType.STRING) {
                            String cid = arr.getString(k);
                            if (selectedIds.add(cid)) {
                                selArr.add(cid);
                            }
                        }
                    }
                }
                sanitizedAnswersBuilder.add(qId, selArr);
                Map<String, Double> weightMap = new HashMap<>();
                JsonArray choices = q.getJsonArray("choices");
                for (int c = 0; c < choices.size(); c++) {
                    JsonObject choice = choices.getJsonObject(c);
                    weightMap.put(choice.getString("id"), choice.getJsonNumber("weight").doubleValue());
                }
                double sumPct = 0.0;
                for (String sel : selectedIds) {
                    sumPct += weightMap.getOrDefault(sel, 0.0);
                }
                qEarned = Math.max(0.0, Math.min(1.0, sumPct / 100.0));
            }

            totalEarnedUnits += qEarned;
            boolean isCorrect = Math.abs(qEarned - 1.0) < 0.001;
            resultsBuilder.add(qId, Json.createObjectBuilder()
                    .add("correct", isCorrect)
                    .add("earned", Math.round(qEarned * perQuestionPoints * 100.0) / 100.0));
        }

        double finalScore = questions.isEmpty() ? 0.0
                : Math.round((totalEarnedUnits / questions.size()) * totalScoreConfig * 100.0) / 100.0;
        long submittedAt = System.currentTimeMillis();
        String displayName = Strings.isNullOrEmpty(ctx.getUserDisplayName()) ? ctx.getUsername() : ctx.getUserDisplayName();
        JsonObject strippedExam = stripAnswersFromExam(exam);

        JsonObject attemptObj = Json.createObjectBuilder()
                .add("exam_id", examId)
                .add("exam_title", exam.getString("title", ""))
                .add("exam_code", exam.getString("exam_code", ""))
                .add("exam_version", exam.getString("exam_version", ""))
                .add("owner_user_id", exam.getString("owner_user_id", ""))
                .add("owner_group_id", exam.getString("owner_group_id", ""))
                .add("owner_label", exam.getString("owner_label", exam.getString("owner_username", "")))
                .add("user_id", ctx.getUserId())
                .add("username", ctx.getUsername())
                .add("display_name", displayName)
                .add("score", finalScore)
                .add("max_score", Math.round(totalScoreConfig * 100.0) / 100.0)
                .add("submitted_at", submittedAt)
                .add("answers", sanitizedAnswersBuilder)
                .add("results", resultsBuilder)
                .add("questions_snapshot", strippedExam.getJsonArray("questions"))
                .build();

        dataDao.upsert(PLUGIN_ID, COLLECTION_ATTEMPTS, attemptKey, ctx.getUserId(), attemptObj.toString());

        return PluginResult.ok(Json.createObjectBuilder()
                .add("attempt", attemptObj)
                .build());
    }

    private PluginResult handleListAttempts(PluginContext ctx) {
        String filterExamId = ctx.getQueryParams().getOrDefault("examId", "").trim();
        List<JsonObject> visible = collectVisibleAttempts(ctx, filterExamId, null, null);
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (JsonObject a : visible) {
            arr.add(a);
        }
        return PluginResult.ok(Json.createObjectBuilder()
                .add("is_top_admin", isTopAdmin(ctx))
                .add("is_teacher", isTeacher(ctx, loadTeachersConfig()))
                .add("attempts", arr)
                .build());
    }

    private PluginResult handleListAclTargets(PluginContext ctx) {
        String examId = ctx.getQueryParams().getOrDefault("examId", "").trim();
        JsonObject exam = loadExam(examId);
        if (exam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        if (!canManageExam(ctx, exam)) {
            return error(403, "ForbiddenError", "Permission denied");
        }

        Set<String> allowedUsers = jsonStringSet(exam, "allowed_users");
        Set<String> allowedGroups = jsonStringSet(exam, "allowed_groups");

        JsonArrayBuilder usersArr = Json.createArrayBuilder();
        for (UserDto u : new UserDao().findByCriteria(new UserCriteria(), null)) {
            if (u.getDisableTimestamp() != null) {
                continue;
            }
            usersArr.add(Json.createObjectBuilder()
                    .add("id", u.getId())
                    .add("username", u.getUsername())
                    .add("name", Strings.nullToEmpty(u.getName()))
                    .add("allowed", allowedUsers.contains(u.getId())));
        }

        JsonArrayBuilder groupsArr = Json.createArrayBuilder();
        for (GroupDto g : new GroupDao().findByCriteria(new GroupCriteria(), null)) {
            groupsArr.add(Json.createObjectBuilder()
                    .add("id", g.getId())
                    .add("name", g.getName())
                    .add("description", Strings.nullToEmpty(g.getDescription()))
                    .add("allowed", allowedGroups.contains(g.getId())));
        }

        return PluginResult.ok(Json.createObjectBuilder()
                .add("exam_id", examId)
                .add("users", usersArr)
                .add("groups", groupsArr)
                .build());
    }

    private PluginResult handleSetExamAcl(PluginContext ctx) {
        JsonObject body = parseJson(ctx.getRequestBody());
        String examId = body.getString("exam_id", "").trim();
        String targetType = body.getString("target_type", "").trim().toUpperCase(Locale.ROOT);
        String targetId = body.getString("target_id", "").trim();
        boolean allow = body.getBoolean("allow", true);

        JsonObject exam = loadExam(examId);
        if (exam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        if (!canManageExam(ctx, exam)) {
            return error(403, "ForbiddenError", "Permission denied");
        }

        String targetLabel;
        if ("USER".equals(targetType)) {
            User targetUser = new UserDao().getById(targetId);
            if (targetUser == null || targetUser.getDeleteDate() != null || targetUser.getDisableDate() != null) {
                return error(400, "InvalidTarget", "Target user does not exist or is disabled");
            }
            targetLabel = targetUser.getUsername();
        } else if ("GROUP".equals(targetType)) {
            Group targetGroup = new GroupDao().getActiveById(targetId);
            if (targetGroup == null || targetGroup.getDeleteDate() != null) {
                return error(400, "InvalidTarget", "Target group does not exist or is deleted");
            }
            targetLabel = targetGroup.getName();
        } else {
            return error(400, "ValidationError", "target_type must be USER or GROUP");
        }

        Set<String> allowedUsers = jsonStringSet(exam, "allowed_users");
        Set<String> allowedGroups = jsonStringSet(exam, "allowed_groups");
        Set<String> targetSet = "USER".equals(targetType) ? allowedUsers : allowedGroups;
        if (allow) {
            targetSet.add(targetId);
        } else {
            targetSet.remove(targetId);
        }

        JsonObjectBuilder updated = Json.createObjectBuilder();
        for (String k : exam.keySet()) {
            if (!"allowed_users".equals(k) && !"allowed_groups".equals(k) && !"updated_at".equals(k)) {
                updated.add(k, exam.get(k));
            }
        }
        updated.add("allowed_users", toJsonArray(allowedUsers));
        updated.add("allowed_groups", toJsonArray(allowedGroups));
        updated.add("updated_at", System.currentTimeMillis());
        new PluginDataDao().upsert(PLUGIN_ID, COLLECTION_EXAMS, examId, ctx.getUserId(), updated.build().toString());

        auditExamAction(ctx, AuditLogType.UPDATE,
                "Exam permission " + (allow ? "granted" : "revoked") + " on " + exam.getString("exam_code", examId)
                        + " for " + targetType + " " + targetLabel);

        return PluginResult.ok(Json.createObjectBuilder().add("status", "ok").build());
    }

    private PluginResult handleListReferenceDocuments(PluginContext ctx) {
        if (!isTopAdmin(ctx) && !isTeacher(ctx, loadTeachersConfig())) {
            return error(403, "ForbiddenError", "Only teachers or top admins can list reference documents");
        }
        String examId = ctx.getQueryParams().getOrDefault("examId", "").trim();
        String query = ctx.getQueryParams().getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
        String sort = ctx.getQueryParams().getOrDefault("sort", "create_date").trim().toLowerCase(Locale.ROOT);
        boolean asc = "true".equalsIgnoreCase(ctx.getQueryParams().getOrDefault("asc", "false").trim());
        int offset = 0;
        int limit = 10;
        try {
            offset = Math.max(0, Integer.parseInt(ctx.getQueryParams().getOrDefault("offset", "0").trim()));
        } catch (NumberFormatException ignored) {
        }
        try {
            limit = Math.max(1, Math.min(100, Integer.parseInt(ctx.getQueryParams().getOrDefault("limit", "10").trim())));
        } catch (NumberFormatException ignored) {
        }

        Set<String> boundIds = Set.of();
        if (!examId.isEmpty()) {
            JsonObject exam = loadExam(examId);
            if (exam == null) {
                return error(404, "ExamNotFound", "Exam not found");
            }
            if (!canManageExam(ctx, exam)) {
                return error(403, "ForbiddenError", "Permission denied");
            }
            boundIds = jsonStringSet(exam, "reference_document_ids");
        }

        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Set<String> excludedTagIds = new HashSet<>(new TagDao().getEffectiveHiddenTagIds());
        List<Tag> adminPluginsTags = em.createQuery(
                "select t from Tag t where t.name = 'plugins' and t.userId = 'admin' and t.deleteDate is null",
                Tag.class)
                .getResultList();
        for (Tag t : adminPluginsTags) {
            excludedTagIds.add(t.getId());
        }
        Set<String> excludedDocIds = new HashSet<>();
        if (!excludedTagIds.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<String> taggedDocIds = em.createQuery(
                    "select distinct dt.documentId from DocumentTag dt where dt.tagId in :tagIds and dt.deleteDate is null")
                    .setParameter("tagIds", excludedTagIds)
                    .getResultList();
            excludedDocIds.addAll(taggedDocIds);
        }

        List<Document> allDocs = em.createQuery(
                "select d from Document d where d.deleteDate is null",
                Document.class)
                .getResultList();

        AclDao aclDao = new AclDao();
        List<Document> filtered = new ArrayList<>();
        for (Document d : allDocs) {
            String title = d.getTitle() == null ? "" : d.getTitle();
            if (!boundIds.contains(d.getId())) {
                if (("plugins".equals(title) && "admin".equals(d.getUserId())) || excludedDocIds.contains(d.getId())) {
                    continue;
                }
            }
            if (!query.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (!aclDao.checkPermission(d.getId(), PermType.READ, ctx.getTargetIdList())) {
                continue;
            }
            filtered.add(d);
        }

        Comparator<Document> comparator;
        if ("title".equals(sort)) {
            comparator = Comparator.comparing(
                    d -> (d.getTitle() == null ? "" : d.getTitle()).toLowerCase(Locale.ROOT));
        } else {
            comparator = Comparator.comparingLong(
                    d -> d.getCreateDate() == null ? 0L : d.getCreateDate().getTime());
        }
        if (!asc) {
            comparator = comparator.reversed();
        }
        filtered.sort(comparator.thenComparing(d -> d.getId() == null ? "" : d.getId()));

        int total = filtered.size();
        int fromIdx = Math.min(offset, total);
        int toIdx = Math.min(fromIdx + limit, total);

        JsonArrayBuilder docsArr = Json.createArrayBuilder();
        for (int i = fromIdx; i < toIdx; i++) {
            Document d = filtered.get(i);
            String title = d.getTitle() == null ? "" : d.getTitle();
            long createDateMs = d.getCreateDate() == null ? 0L : d.getCreateDate().getTime();
            docsArr.add(Json.createObjectBuilder()
                    .add("id", d.getId())
                    .add("title", title)
                    .add("create_date", createDateMs)
                    .add("allowed", boundIds.contains(d.getId())));
        }
        return PluginResult.ok(Json.createObjectBuilder()
                .add("total", total)
                .add("offset", offset)
                .add("limit", limit)
                .add("documents", docsArr)
                .build());
    }

    private PluginResult handleSetExamReferenceDocuments(PluginContext ctx) {
        JsonObject body = parseJson(ctx.getRequestBody());
        String examId = body.getString("exam_id", "").trim();
        JsonObject exam = loadExam(examId);
        if (exam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        if (!canManageExam(ctx, exam)) {
            return error(403, "ForbiddenError", "Permission denied");
        }

        AclDao aclDao = new AclDao();
        DocumentDao docDao = new DocumentDao();
        Set<String> refDocIds = new LinkedHashSet<>(jsonStringSet(exam, "reference_document_ids"));

        if (body.containsKey("reference_document_ids") && body.get("reference_document_ids").getValueType() == JsonValue.ValueType.ARRAY) {
            Set<String> nextSet = new LinkedHashSet<>();
            JsonArray arr = body.getJsonArray("reference_document_ids");
            for (int i = 0; i < arr.size(); i++) {
                String docId = arr.getString(i, "").trim();
                if (docId.isEmpty()) {
                    continue;
                }
                if (!refDocIds.contains(docId)) {
                    if (docDao.getById(docId) == null || !aclDao.checkPermission(docId, PermType.READ, ctx.getTargetIdList())) {
                        return error(403, "ForbiddenError", "No READ permission on reference document: " + docId);
                    }
                }
                nextSet.add(docId);
            }
            refDocIds = nextSet;
        } else {
            String docId = body.getString("document_id", "").trim();
            boolean allow = body.getBoolean("allow", true);
            if (docId.isEmpty()) {
                return error(400, "ValidationError", "document_id is required");
            }
            if (allow) {
                if (docDao.getById(docId) == null || !aclDao.checkPermission(docId, PermType.READ, ctx.getTargetIdList())) {
                    return error(403, "ForbiddenError", "No READ permission on reference document: " + docId);
                }
                refDocIds.add(docId);
            } else {
                refDocIds.remove(docId);
            }
        }

        JsonObjectBuilder updated = Json.createObjectBuilder();
        for (String k : exam.keySet()) {
            if (!"reference_document_ids".equals(k) && !"updated_at".equals(k)) {
                updated.add(k, exam.get(k));
            }
        }
        updated.add("reference_document_ids", toJsonArray(refDocIds));
        updated.add("updated_at", System.currentTimeMillis());
        JsonObject savedObj = updated.build();
        new PluginDataDao().upsert(PLUGIN_ID, COLLECTION_EXAMS, examId, ctx.getUserId(), savedObj.toString());

        auditExamAction(ctx, AuditLogType.UPDATE,
                "Exam reference documents updated on " + exam.getString("exam_code", examId) + " (count=" + refDocIds.size() + ")");

        return PluginResult.ok(Json.createObjectBuilder()
                .add("status", "ok")
                .add("exam", buildExamSummary(savedObj, null, countAttemptsForExam(examId), true, canTakeExam(ctx, savedObj), ctx.getTargetIdList()))
                .build());
    }

    private PluginResult handleExportBlankPdf(PluginContext ctx) throws Exception {
        String examId = ctx.getQueryParams().getOrDefault("examId", "").trim();
        String lang = ctx.getQueryParams().getOrDefault("lang", "en").trim();
        JsonObject exam = loadExam(examId);
        if (exam == null) {
            return error(404, "ExamNotFound", "Exam not found");
        }
        if (!canManageExam(ctx, exam) && !canTakeExam(ctx, exam)) {
            return error(403, "ForbiddenError", "No permission for this exam");
        }
        byte[] pdfBytes = ExamPdfExporter.exportBlankPdf(stripAnswersFromExam(localizeExam(exam, lang)), lang);
        String fileName = sanitizeFileName(exam.getString("exam_code", "exam") + "_v" + exam.getString("exam_version", "1")) + "_blank.pdf";
        return PluginResult.binary(pdfBytes, "application/pdf", fileName);
    }

    private PluginResult handleExportAttemptPdf(PluginContext ctx) throws Exception {
        String examId = ctx.getQueryParams().getOrDefault("examId", "").trim();
        String lang = ctx.getQueryParams().getOrDefault("lang", "en").trim();
        String targetUserId = ctx.getQueryParams().getOrDefault("userId", ctx.getUserId()).trim();
        if (targetUserId.isEmpty()) {
            targetUserId = ctx.getUserId();
        }

        PluginData attemptRow = new PluginDataDao().getByKey(PLUGIN_ID, COLLECTION_ATTEMPTS, examId + ":" + targetUserId);
        if (attemptRow == null) {
            return error(404, "AttemptNotFound", "No exam attempt found");
        }
        JsonObject attempt = parseJson(attemptRow.getValue());
        JsonObject exam = loadExam(examId);

        if (!canViewAttempt(ctx, attempt, exam)) {
            return error(403, "ForbiddenError", "Permission denied");
        }

        JsonObject pdfExam;
        if (exam != null) {
            pdfExam = stripAnswersFromExam(localizeExam(exam, lang));
        } else {
            pdfExam = Json.createObjectBuilder()
                    .add("id", examId)
                    .add("title", attempt.getString("exam_title", "Deleted Exam"))
                    .add("exam_code", attempt.getString("exam_code", "-"))
                    .add("exam_version", attempt.getString("exam_version", "-"))
                    .add("questions", attempt.containsKey("questions_snapshot")
                            ? attempt.getJsonArray("questions_snapshot")
                            : Json.createArrayBuilder().build())
                    .build();
        }

        byte[] pdfBytes = ExamPdfExporter.exportAttemptPdf(pdfExam, attempt, lang);
        String fileName = sanitizeFileName(pdfExam.getString("exam_code", "exam") + "_v" + pdfExam.getString("exam_version", "1")
                + "_" + attempt.getString("username", "user")) + ".pdf";
        return PluginResult.binary(pdfBytes, "application/pdf", fileName);
    }

    private PluginResult handleExportSummaryPdf(PluginContext ctx) throws Exception {
        String fromStr = ctx.getQueryParams().getOrDefault("from", "").trim();
        String toStr = ctx.getQueryParams().getOrDefault("to", "").trim();
        String filterExamId = ctx.getQueryParams().getOrDefault("examId", "").trim();
        String lang = ctx.getQueryParams().getOrDefault("lang", "en").trim();

        if (fromStr.isEmpty() || toStr.isEmpty()) {
            return error(400, "ValidationError", "Date range (from, to) is required");
        }

        Long fromMillis;
        Long toMillis;
        try {
            ZoneId zone = ZoneId.systemDefault();
            fromMillis = LocalDate.parse(fromStr).atStartOfDay(zone).toInstant().toEpochMilli();
            toMillis = LocalDate.parse(toStr).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L;
        } catch (Exception e) {
            return error(400, "ValidationError", "Invalid date format (expected YYYY-MM-DD)");
        }

        List<JsonObject> filtered = collectVisibleAttempts(ctx, filterExamId, fromMillis, toMillis);
        filtered.sort(Comparator
                .comparing((JsonObject a) -> a.getString("exam_code", ""))
                .thenComparing((JsonObject a) -> a.containsKey("submitted_at") ? a.getJsonNumber("submitted_at").longValue() : 0L, Comparator.reverseOrder()));

        String examFilterLabel = "";
        if (!filterExamId.isEmpty()) {
            JsonObject exam = loadExam(filterExamId);
            if (exam != null) {
                examFilterLabel = exam.getString("exam_code", "") + " - " + exam.getString("title", "");
            }
        }
        String displayName = Strings.isNullOrEmpty(ctx.getUserDisplayName()) ? ctx.getUsername() : ctx.getUserDisplayName();
        String exportedBy = displayName + " (" + ctx.getUsername() + ")";

        byte[] pdfBytes = ExamPdfExporter.exportSummaryPdf(filtered, fromStr, toStr, exportedBy, examFilterLabel, lang);
        String fileName = sanitizeFileName("exam_summary_" + fromStr + "_to_" + toStr) + ".pdf";
        return PluginResult.binary(pdfBytes, "application/pdf", fileName);
    }

    private List<JsonObject> collectVisibleAttempts(PluginContext ctx, String filterExamId, Long fromMillis, Long toMillis) {
        PluginDataDao dataDao = new PluginDataDao();
        Map<String, JsonObject> examMap = new HashMap<>();
        for (PluginData row : dataDao.findByCollection(PLUGIN_ID, COLLECTION_EXAMS)) {
            JsonObject e = parseJson(row.getValue());
            examMap.put(e.getString("id", ""), e);
        }

        List<JsonObject> out = new ArrayList<>();
        for (PluginData row : dataDao.findByCollection(PLUGIN_ID, COLLECTION_ATTEMPTS)) {
            JsonObject attempt = parseJson(row.getValue());
            String examId = attempt.getString("exam_id", "");
            if (!filterExamId.isEmpty() && !filterExamId.equals(examId)) {
                continue;
            }
            if (fromMillis != null && toMillis != null) {
                long ts = attempt.containsKey("submitted_at") ? attempt.getJsonNumber("submitted_at").longValue() : 0L;
                if (ts < fromMillis || ts > toMillis) {
                    continue;
                }
            }
            if (!canViewAttempt(ctx, attempt, examMap.get(examId))) {
                continue;
            }
            out.add(attempt);
        }
        return out;
    }

    private boolean isTopAdmin(PluginContext ctx) {
        if (ctx.isAdmin()) {
            return true;
        }
        PluginTag mgmtTag = new PluginTagDao().findByPluginIdAndRole(PLUGIN_ID, "MANAGEMENT");
        if (mgmtTag == null) {
            return false;
        }
        return new AclDao().checkPermission(mgmtTag.getTagId(), PermType.READ, ctx.getTargetIdList());
    }

    private boolean isTeacher(PluginContext ctx, JsonObject teachersCfg) {
        if (isTopAdmin(ctx)) {
            return true;
        }
        Set<String> users = jsonStringSet(teachersCfg, "users");
        if (users.contains(ctx.getUserId())) {
            return true;
        }
        Set<String> groups = jsonStringSet(teachersCfg, "groups");
        for (String targetId : ctx.getTargetIdList()) {
            if (groups.contains(targetId)) {
                return true;
            }
        }
        return false;
    }

    private boolean canManageExam(PluginContext ctx, JsonObject exam) {
        if (isTopAdmin(ctx)) {
            return true;
        }
        if (!isTeacher(ctx, loadTeachersConfig())) {
            return false;
        }
        String ownerUserId = exam.getString("owner_user_id", "");
        String ownerGroupId = exam.getString("owner_group_id", "");
        return ctx.getUserId().equals(ownerUserId)
                || (!ownerGroupId.isEmpty() && ctx.getTargetIdList().contains(ownerGroupId));
    }

    private boolean canTakeExam(PluginContext ctx, JsonObject exam) {
        Set<String> allowedUsers = jsonStringSet(exam, "allowed_users");
        if (allowedUsers.contains(ctx.getUserId())) {
            return true;
        }
        Set<String> allowedGroups = jsonStringSet(exam, "allowed_groups");
        for (String targetId : ctx.getTargetIdList()) {
            if (allowedGroups.contains(targetId)) {
                return true;
            }
        }
        // Backward compatibility if an older exam used a tag_id
        String legacyTagId = exam.getString("tag_id", "");
        if (!legacyTagId.isEmpty() && allowedUsers.isEmpty() && allowedGroups.isEmpty()) {
            return new AclDao().checkPermission(legacyTagId, PermType.READ, ctx.getTargetIdList());
        }
        return false;
    }

    private boolean canViewAttempt(PluginContext ctx, JsonObject attempt, JsonObject exam) {
        if (isTopAdmin(ctx)) {
            return true;
        }
        if (ctx.getUserId().equals(attempt.getString("user_id", ""))) {
            return true;
        }
        if (!isTeacher(ctx, loadTeachersConfig())) {
            return false;
        }
        String ownerUserId = attempt.getString("owner_user_id", exam != null ? exam.getString("owner_user_id", "") : "");
        String ownerGroupId = attempt.getString("owner_group_id", exam != null ? exam.getString("owner_group_id", "") : "");
        return ctx.getUserId().equals(ownerUserId)
                || (!ownerGroupId.isEmpty() && ctx.getTargetIdList().contains(ownerGroupId));
    }

    private JsonObject loadTeachersConfig() {
        PluginData row = new PluginDataDao().getByKey(PLUGIN_ID, COLLECTION_CONFIG, KEY_TEACHERS);
        return row == null ? Json.createObjectBuilder().build() : parseJson(row.getValue());
    }

    private void auditExamAction(PluginContext ctx, AuditLogType type, String message) {
        PluginTag mgmtTag = new PluginTagDao().findByPluginIdAndRole(PLUGIN_ID, "MANAGEMENT");
        if (mgmtTag != null) {
            Tag tag = new TagDao().getById(mgmtTag.getTagId());
            if (tag != null) {
                AuditLogUtil.create(tag, type, ctx.getUserId(), message);
            }
        }
    }

    private int countAttemptsForExam(String examId) {
        int count = 0;
        for (PluginData row : new PluginDataDao().findByCollection(PLUGIN_ID, COLLECTION_ATTEMPTS)) {
            if (row.getKey() != null && row.getKey().startsWith(examId + ":")) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Integer> countAllAttemptsByExam() {
        Map<String, Integer> map = new HashMap<>();
        for (PluginData row : new PluginDataDao().findByCollection(PLUGIN_ID, COLLECTION_ATTEMPTS)) {
            JsonObject att = parseJson(row.getValue());
            String eId = att.getString("exam_id", "");
            if (!eId.isEmpty()) {
                map.put(eId, map.getOrDefault(eId, 0) + 1);
            }
        }
        return map;
    }

    private JsonObject loadExam(String examId) {
        if (Strings.isNullOrEmpty(examId)) {
            return null;
        }
        PluginData row = new PluginDataDao().getByKey(PLUGIN_ID, COLLECTION_EXAMS, examId);
        return row == null ? null : parseJson(row.getValue());
    }

    private JsonObject localizeExam(JsonObject exam, String lang) {
        if (exam == null || !exam.containsKey("i18n") || exam.get("i18n").getValueType() != JsonValue.ValueType.OBJECT) {
            return exam;
        }
        JsonObject i18n = exam.getJsonObject("i18n");
        String code = Strings.isNullOrEmpty(lang) ? "en" : lang.trim();
        JsonObject loc = i18n.containsKey(code) ? i18n.getJsonObject(code) : null;
        if (loc == null && code.contains("_")) {
            String base = code.split("_")[0];
            if (i18n.containsKey(base)) {
                loc = i18n.getJsonObject(base);
            }
        }
        if (loc == null && i18n.containsKey("en")) {
            loc = i18n.getJsonObject("en");
        }
        if (loc == null) {
            return exam;
        }

        JsonArray origQuestions = exam.getJsonArray("questions");
        JsonArrayBuilder localizedQuestions = Json.createArrayBuilder();
        for (int i = 0; i < origQuestions.size(); i++) {
            JsonObject q = origQuestions.getJsonObject(i);
            String qId = q.getString("id", "q" + (i + 1));
            JsonObjectBuilder qb = Json.createObjectBuilder();
            for (String k : q.keySet()) {
                if (!"prompt".equals(k) && !"choices".equals(k)) {
                    qb.add(k, q.get(k));
                }
            }
            qb.add("prompt", loc.getString(qId, q.getString("prompt", "")));
            if (q.containsKey("choices")) {
                JsonArray origChoices = q.getJsonArray("choices");
                JsonArray locChoices = loc.containsKey(qId + "_choices") && loc.get(qId + "_choices").getValueType() == JsonValue.ValueType.ARRAY
                        ? loc.getJsonArray(qId + "_choices")
                        : null;
                JsonArrayBuilder cbArr = Json.createArrayBuilder();
                for (int c = 0; c < origChoices.size(); c++) {
                    JsonObject ch = origChoices.getJsonObject(c);
                    JsonObjectBuilder chb = Json.createObjectBuilder();
                    for (String ck : ch.keySet()) {
                        if (!"text".equals(ck)) {
                            chb.add(ck, ch.get(ck));
                        }
                    }
                    String text = (locChoices != null && c < locChoices.size()) ? locChoices.getString(c, ch.getString("text", "")) : ch.getString("text", "");
                    chb.add("text", text);
                    cbArr.add(chb);
                }
                qb.add("choices", cbArr);
            }
            localizedQuestions.add(qb);
        }

        JsonObjectBuilder eb = Json.createObjectBuilder();
        for (String k : exam.keySet()) {
            if (!"title".equals(k) && !"questions".equals(k)) {
                eb.add(k, exam.get(k));
            }
        }
        eb.add("title", loc.getString("title", exam.getString("title", "")));
        eb.add("questions", localizedQuestions);
        return eb.build();
    }

    private JsonObject stripAnswersFromExam(JsonObject exam) {
        return stripAnswersFromExam(exam, null);
    }

    private JsonObject stripAnswersFromExam(JsonObject exam, List<String> viewerTargetIds) {
        JsonArrayBuilder strippedQuestions = Json.createArrayBuilder();
        JsonArray questions = exam.getJsonArray("questions");
        for (int i = 0; i < questions.size(); i++) {
            JsonObject q = questions.getJsonObject(i);
            JsonObjectBuilder qb = Json.createObjectBuilder()
                    .add("id", q.getString("id"))
                    .add("title", q.getString("title", ""))
                    .add("type", q.getString("type"))
                    .add("prompt", q.getString("prompt"));
            if (q.containsKey("choices")) {
                JsonArrayBuilder cbArr = Json.createArrayBuilder();
                JsonArray choices = q.getJsonArray("choices");
                for (int c = 0; c < choices.size(); c++) {
                    JsonObject choice = choices.getJsonObject(c);
                    cbArr.add(Json.createObjectBuilder()
                            .add("id", choice.getString("id"))
                            .add("text", choice.getString("text")));
                }
                qb.add("choices", cbArr);
            }
            strippedQuestions.add(qb);
        }

        return Json.createObjectBuilder()
                .add("id", exam.getString("id"))
                .add("title", exam.getString("title", ""))
                .add("exam_code", exam.getString("exam_code", ""))
                .add("exam_version", exam.getString("exam_version", ""))
                .add("owner_label", exam.getString("owner_label", exam.getString("owner_username", "")))
                .add("retake_allowed", exam.getBoolean("retake_allowed", false))
                .add("enabled", exam.getBoolean("enabled", true))
                .add("total_score", exam.containsKey("total_score") ? exam.getJsonNumber("total_score").doubleValue() : DEFAULT_TOTAL_SCORE)
                .add("question_count", questions.size())
                .add("reference_documents", resolveReadableReferenceDocuments(exam, viewerTargetIds))
                .add("questions", strippedQuestions)
                .build();
    }

    private JsonObject buildExamSummary(JsonObject exam, JsonObject myAttempt, int attemptCount, boolean canManage, boolean canTake, List<String> viewerTargetIds) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("id", exam.getString("id"))
                .add("title", exam.getString("title", ""))
                .add("exam_code", exam.getString("exam_code", ""))
                .add("exam_version", exam.getString("exam_version", ""))
                .add("owner_label", exam.getString("owner_label", exam.getString("owner_username", "")))
                .add("owner_group_name", exam.getString("owner_group_name", ""))
                .add("can_manage", canManage)
                .add("can_take", canTake)
                .add("retake_allowed", exam.getBoolean("retake_allowed", false))
                .add("enabled", exam.getBoolean("enabled", true))
                .add("total_score", exam.containsKey("total_score") ? exam.getJsonNumber("total_score").doubleValue() : DEFAULT_TOTAL_SCORE)
                .add("question_count", exam.getInt("question_count", 0))
                .add("reference_documents", resolveReadableReferenceDocuments(exam, viewerTargetIds))
                .add("updated_at", exam.containsKey("updated_at") ? exam.getJsonNumber("updated_at").longValue() : 0L);
        if (canManage) {
            b.add("attempt_count", attemptCount);
        }
        if (myAttempt != null) {
            b.add("my_attempt", Json.createObjectBuilder()
                    .add("score", myAttempt.getJsonNumber("score").doubleValue())
                    .add("max_score", myAttempt.getJsonNumber("max_score").doubleValue())
                    .add("submitted_at", myAttempt.getJsonNumber("submitted_at").longValue()));
        }
        return b.build();
    }

    private JsonArray resolveReadableReferenceDocuments(JsonObject exam, List<String> viewerTargetIds) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        if (exam == null || viewerTargetIds == null || viewerTargetIds.isEmpty()) {
            return arr.build();
        }
        if (!exam.containsKey("reference_document_ids") || exam.get("reference_document_ids").getValueType() != JsonValue.ValueType.ARRAY) {
            return arr.build();
        }
        AclDao aclDao = new AclDao();
        DocumentDao docDao = new DocumentDao();
        JsonArray rawIds = exam.getJsonArray("reference_document_ids");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rawIds.size(); i++) {
            String docId = rawIds.getString(i, "").trim();
            if (docId.isEmpty() || !seen.add(docId)) {
                continue;
            }
            Document doc = docDao.getById(docId);
            if (doc == null) {
                continue;
            }
            if (!aclDao.checkPermission(docId, PermType.READ, viewerTargetIds)) {
                continue;
            }
            arr.add(Json.createObjectBuilder()
                    .add("id", doc.getId())
                    .add("title", doc.getTitle() == null ? "" : doc.getTitle()));
        }
        return arr.build();
    }

    private static Set<String> jsonStringSet(JsonObject obj, String key) {
        Set<String> set = new HashSet<>();
        if (obj != null && obj.containsKey(key) && obj.get(key).getValueType() == JsonValue.ValueType.ARRAY) {
            JsonArray arr = obj.getJsonArray(key);
            for (int i = 0; i < arr.size(); i++) {
                set.add(arr.getString(i));
            }
        }
        return set;
    }

    private static JsonArray toJsonArray(Set<String> set) {
        JsonArrayBuilder b = Json.createArrayBuilder();
        for (String s : set) {
            b.add(s);
        }
        return b.build();
    }

    private static JsonObject parseJson(String raw) {
        if (Strings.isNullOrEmpty(raw)) {
            return Json.createObjectBuilder().build();
        }
        try (JsonReader reader = Json.createReader(new StringReader(raw))) {
            return reader.readObject();
        }
    }

    private static PluginResult error(int status, String type, String message) {
        return PluginResult.error(status, Json.createObjectBuilder()
                .add("type", type)
                .add("message", message)
                .build());
    }

    private static String sanitizeFileName(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
