# 線上考試外掛 (`online-exam`) — 專屬架構與樣板指南

`online-exam` 是 **FormosaBearV Docs** 的官方全端示範外掛。您可以直接參考或複製本目錄（`docs-plugins/online-exam/`）與後端 `ExamPluginHandler`，作為開發進階第三方外掛的標準樣板。

---

## 1. 程式碼與檔案對照表

| 模組 | 檔案路徑 | 職責說明 |
| --- | --- | --- |
| 外掛描述檔 | [plugin.json](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/online-exam/plugin.json) | 宣告版號（`1.3.0`）、16 國 `nav_label_i18n`，以及單一隱藏保護標籤 `Online Exam Admin`（`MANAGEMENT`）。 |
| 多語系字典 | [locale/](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/online-exam/locale) | 完整 16 國語系檔（含繁中、簡中、歐陸葡語、巴西葡語等慣用語及 `error.*` 錯誤碼翻譯）。 |
| 前端介面 | [ui/](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/online-exam/ui) | 零外部 CDN 依賴的 HTML/CSS/JS，含動態 `ACTION_BASE`、漏答提醒與三層角色分頁。 |
| 後端處理器 | [ExamPluginHandler.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/plugin/exam/ExamPluginHandler.java) | 實作三層權限隔離、GIFT 匯入、100 分均分批改、剝除正解與 `AuditLog` 稽核紀錄。 |
| GIFT 解析器 | [GiftParser.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/plugin/exam/GiftParser.java) | 解析單選、多選（`%weight%`）、是非題，內建 `512 KB` / `500` 題 DoS 上限防護。 |
| PDF 匯出引擎 | [ExamPdfExporter.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/plugin/exam/ExamPdfExporter.java) | OpenPDF + 內建 `Noto Sans CJK TC` 字型，產製空白考卷 PDF、當時時間浮水印作答 PDF 與多語系橫向成績彙總表 PDF。 |
| 發布壓縮包 | [online-exam.zip](file:///W:/developer/project/github/FormosaBearV-Docs/docs-plugins/dist/online-exam.zip) | 可直接於「設定 → 外掛管理」上傳安裝之套件包。 |

---

## 2. 三層權限模型設計

1. **最高管理員**（系統 `ADMIN` 或具備 `Online Exam Admin` 標籤讀取權限）：
   - 可透過「出題老師授權」勾選啟用中的帳號或群組（例如 `OnlineExam01`、`OnlineExam02`）。
   - 可檢視、管理、停用、刪除**所有老師**的考卷，並匯出全體成績彙總表 PDF。
2. **各科出題老師**（被最高管理員指派之帳號或群組）：
   - 可建立並管理自己名下或同老師群組的考卷（記錄 `owner_user_id`、`owner_group_id` 與報表識別欄位 `owner_label`）。
   - 與其他老師群組完全隔離，無法查看或修改他科老師的考題與成績。
   - 可為自己名下考卷指派啟用中的帳號或群組應試。
3. **應試學生**：
   - 僅能看見自己（帳號或所屬群組）被授權且啟用中的考卷。
   - 下傳題目已剝除正解與權重；交卷由後端批改並僅顯示對錯與總分，支援空白 PDF、個人浮水印作答 PDF 及個人時間區間成績彙總表 PDF 下載。

---

## 3. `T_PLUGIN_DATA` 資料集合設計 (`pluginId = "online-exam"`)

- **`config` / `teachers`**：`{ "users": [...], "groups": [...] }`（出題老師名單）。
- **`exams` / `<examId>`**：考卷基本資訊、`owner_label`、`allowed_users`、`allowed_groups`、`total_score`（預設 100 分均分）、`enabled`、`retake_allowed` 與題目正解（絕不下傳至考生端）。
- **`attempts` / `<examId>:<userId>`**：作答成績紀錄，含 `score`、`max_score`、`submitted_at`、`owner_label`、`results`（僅 `correct: true/false`）與 `questions_snapshot`（已去正解之題目快照，確保日後考卷刪除仍可匯出歷史作答 PDF）。

---

## 4. 如何以此樣板複製開發新外掛

1. 複製 `docs-plugins/online-exam/` 為 `docs-plugins/my-plugin/`，修改 `plugin.json` 的 `id`、`name`、`handler`、`nav_label_i18n` 與 `protected_tags`。
2. 以 `locale/en.json` 為基準維護多語系翻譯與 `error.*` 錯誤碼。
3. 若需自訂後端邏輯或匯出報表，建立 `MyPluginHandler implements PluginHandler` 並於 `PluginService` 靜態區塊註冊。
4. 將 `docs-plugins/my-plugin/*` 打包為 `.zip`（確保 `plugin.json` 位於壓縮檔根目錄），至「設定 → 外掛管理」上傳並啟用即可。
