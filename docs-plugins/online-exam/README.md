# Online Exam Plugin — Reference Template & Architecture Guide

`online-exam` is the official full-stack showcase plugin for **FormosaBearV Docs**. You can use this directory (`docs-plugins/online-exam/`) together with `ExamPluginHandler` as a production-grade template for building your own advanced plugins.

---

## 1. Directory & Code Map

| Component | Location | Responsibility |
| --- | --- | --- |
| Manifest | [plugin.json](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/online-exam/plugin.json) | Plugin metadata (`version: 1.3.0`), 16-locale `nav_label_i18n`, single hidden `MANAGEMENT` tag (`Online Exam Admin`). |
| Locales | [locale/](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/online-exam/locale) | Complete 16-language JSON translations (`en.json`, `zh_TW.json`, `zh_CN.json`, `pt.json`, `pt_BR.json`, etc.) with `error.*` mappings. |
| Frontend UI | [ui/](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/online-exam/ui) | Self-contained HTML/CSS/JS with zero external dependencies, dynamic `ACTION_BASE`, unanswered-question guard, and role-based tabs. |
| Backend Handler | [ExamPluginHandler.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/plugin/exam/ExamPluginHandler.java) | Three-tier RBAC, GIFT import, 100-pt equal grading, answer stripping, audit logging, and action dispatch. |
| GIFT Parser | [GiftParser.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/plugin/exam/GiftParser.java) | Moodle GIFT parser (Single Choice, Multiple Choice with `%weight%`, True/False) with 512 KB / 500-question DoS limits. |
| PDF Engine | [ExamPdfExporter.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/plugin/exam/ExamPdfExporter.java) | OpenPDF + bundled `Noto Sans CJK TC` generator for blank PDFs, watermarked attempt PDFs, and localized landscape summary reports. |
| Distributable ZIP | [plugin-online-exam.zip](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/dist/plugin-online-exam.zip) | Ready-to-upload package for `#/settings/plugins`. |

---

## 2. Three-Tier Permission Model

1. **Top Admin** (`ADMIN` base function or `READ` ACL on `Online Exam Admin` tag):
   - Authorizes active users or groups (e.g., `OnlineExam01`, `OnlineExam02`) as **Subject Teachers** (`list_teachers` / `set_teacher`).
   - Views, edits, toggles, deletes, and exports summary PDFs for **all** teachers' exams.
2. **Subject Teacher** (user or group member authorized by Top Admin):
   - Creates and manages exams owned by themselves or their authorized teacher group (`owner_user_id` / `owner_group_id`).
   - Isolated from other teacher groups' exams and scores.
   - Assigns active users/groups (`allowed_users` / `allowed_groups`) to take their exams.
3. **Examinee**:
   - Views only enabled exams they are authorized to take (`allowed_users` / `allowed_groups`).
   - Receives stripped questions (no answers/weights), submits for backend grading, views own scores/correctness only, and exports own watermarked attempt PDF or personal summary PDF.

---

## 3. Data Collections in `T_PLUGIN_DATA` (`pluginId = "online-exam"`)

- **`config` / `teachers`**: `{ "users": [...], "groups": [...] }`
- **`exams` / `<examId>`**: Exam metadata, `owner_label`, `allowed_users`, `allowed_groups`, `total_score`, `enabled`, `retake_allowed`, and `questions` (including answer keys/weights, never sent to examinees).
- **`attempts` / `<examId>:<userId>`**: Examinee attempt record including `score`, `max_score`, `submitted_at`, `owner_label`, `results` (`correct: true/false`), and `questions_snapshot` (stripped of answers so historical PDFs still render if the exam is later deleted).

---

## 4. How to Clone This Template for a New Plugin

1. Copy `docs-plugins/online-exam/` to `docs-plugins/my-plugin/` and update `plugin.json` (`id`, `name`, `handler`, `nav_label_i18n`, `protected_tags`).
2. Keep `locale/en.json` as your base dictionary and add/update translations for other locales.
3. If your plugin needs custom backend logic or PDF generation, create `MyPluginHandler implements PluginHandler` and register it in `PluginService` static block.
4. Package `docs-plugins/my-plugin/*` into `my-plugin.zip` (ensuring `plugin.json` is at the ZIP root) and upload via **Settings → Plugins**.
