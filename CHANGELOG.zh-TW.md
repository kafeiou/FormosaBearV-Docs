[ English ](CHANGELOG.md) | [ 繁體中文 ](CHANGELOG.zh-TW.md)

# FormosaBearV 版本更新紀錄 (Changelog)

本專案記錄自版本 `v2.01`（Teedy 現代化重構架構）以來的各版本更新歷程、安全性強化、架構改進與錯誤修復。

---

## [v2.03] - 2026-11-10

> **基準版本**: Git Tag `2.02` (`53cc6936`) ➜ `v2.03`

### 🛡️ 安全漏洞防護 (Security Hardening)
- **CWE-90: LDAP Injection 漏洞修復** (`0f464186` / [Issue #797](https://github.com/sismics/docs/issues/797))
  - **問題描述**：啟用 LDAP 身分驗證時，使用者登入輸入之帳號名稱原先直接以 `String.replace()` 填入 LDAP 搜尋過濾器，缺乏字元轉義。攻擊者可傳入 `username=*` 將過濾式擴展為 `(uid=*)`，進而匹配目錄中所有項目，並在密碼符合時以任意使用者身分登入。
  - **修復內容**：
    - 引入 RFC 4515 標準之 `FilterEncoder.encodeFilterValue()`，嚴格轉義 LDAP 特殊元字元（反斜線 `\`、星號 `*`、括號 `()` 與 NUL 字元）。
    - 於 `UserResource` 登入端點加入 `validateUsername()` 格式校驗作為縱深防禦措施，限制帳號格式僅允許 `[a-zA-Z0-9_@.-]+`。
- **gh-2432: 前端 jQuery 跨站腳本 (XSS) 潛在風險緩解** (`cb09c3c8` / upstream `ee172985`)
  - 修復 `docs-web/src/main/webapp/src/lib/jquery.js` 中對特定 HTML 標籤內容解析之潛在 XSS 漏洞。
- **CWE-79: 儲存型 XSS (Stored XSS) 檔案下載與 MIME 混淆防禦** (`05fb4474` / [Issue #796](https://github.com/sismics/docs/issues/796))
  - **問題描述**：使用者上傳惡意主動式檔案（如包含 JavaScript 的 HTML、SVG、XHTML）後，若瀏覽器在同源環境下 inline 渲染，可造成儲存型 XSS 攻擊；且 `auth_token` 原先未具備 HttpOnly 屬性，易遭 `document.cookie` 讀取洩漏。
  - **修復內容**：
    - 在 [FileResource.java](file:///W:/developer/project/github/teedydocs/docs-web/src/main/java/com/sismics/docs/rest/resource/FileResource.java) 引入 `isActiveMime()` 工具方法，自動辨識主動式 MIME 類型（`text/html`、`image/svg+xml`、`text/xml`、`application/xhtml+xml` 等）。
    - 針對主動式檔案下載，強制將 MIME 類型重映射為 `application/octet-stream`，並強制宣告 `Content-Disposition: attachment`。
    - 加入安全回應標頭 `X-Content-Type-Options: nosniff`，禁止瀏覽器自動進行 MIME 嗅探。
    - 全面加入防快取標頭 (`Cache-Control: private, no-cache, no-store`)，避免敏感檔案快取。
    - 全面相容 RFC 5987 / RFC 6266 標準，導入 `filename*=UTF-8''` 國際化檔名編碼，徹底解決跨瀏覽器與跨平台（Windows/Linux/macOS）下載非 ASCII 中文字元檔名之亂碼問題。
- **預設管理員憑證強化 (Default Credential Hardening)**
  - 系統初次安裝之預設管理員密碼自弱密碼 `admin` 全面升級為高強度預設密碼 `superSecure`。
  - 資料庫初始化腳本 [dbupdate-000-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-000-0.sql) 更新為 10 輪 BCrypt 雜湊值（`$2a$10$iGnrUPOrJfn.cowMyfrSfOow5vD0nxHK8JAyIfxbztA8RtLL/IhmC`）。
  - 同步更新系統常數 [Constants.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/constant/Constants.java#L23)、自動化測試套件與五國語言說明手冊。
- **LDAP 認證來源標記與雙重密碼後門阻絕 (Authentication Source Tagging & Dual-Password Prevention)**
  - **問題描述**：LDAP 帳號若透過管理員或特定 API 設置了本地密碼，會造成該帳號同時接受 LDAP 密碼與本地密碼登入，形成「雙密碼同時有效」之後門與身分驗證混淆風險。
  - **修復內容**：
    - 在 `T_USER` 表新增 `USE_AUTH_SOURCE_C varchar(20) default 'INTERNAL'` 欄位（資料庫版本升級為 36：[dbupdate-036-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-036-0.sql)）。
    - 建立四道防禦縱深：
      1. [InternalAuthenticationHandler.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/InternalAuthenticationHandler.java)：凡帳號標記為 `LDAP`，直接拒絕本地密碼驗證。
      2. [LdapAuthenticationHandler.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/LdapAuthenticationHandler.java)：LDAP 驗證成功時自動標記帳號認證來源為 `LDAP`；`admin` 帳號永遠鎖定為 `INTERNAL` 保護。
      3. [UserResource.java](file:///W:/developer/project/github/teedydocs/docs-web/src/main/java/com/sismics/docs/rest/resource/UserResource.java)：嚴格阻絕 LDAP 帳號自行修改密碼或透過「忘記密碼」申請重設信件（拋出 `LdapUserPasswordChangeForbidden` 與 `LdapUserPasswordResetForbidden`）。
      4. 管理員後台編輯支援「轉為本地帳號 (Convert to Local Account)」對話框與 API，將認證來源解綁轉為 `INTERNAL` 並重設新密碼。
    - 前端介面（帳號設定、使用者列表與編輯頁面）自動顯示 `[LDAP]` 徽章，隱藏不適用的密碼修改輸入框與重設按鈕。

### ✨ 新增功能與系統改進 (New Features & System Improvements)
- **後台基礎網址 (Base URL) 可視化維護**：
  - 新增 `ConfigType.BASE_URL`，於「系統設定 > 一般設定」提供輸入介面，預設為 `http://localhost:8080`。
  - 徹底擺脫對作業系統環境變數 `DOCS_BASE_URL` 的強制依賴，發送信件或外部連結開箱即用。
  - 內建智慧防呆機制：自動補全 `http://` 協定，自動清除尾端多餘斜線 `/` 防止雙斜線。
- **郵件寄件者名稱 (Sender Name) 與系統品牌化**：
  - 新增 `ConfigType.SMTP_FROM_NAME`，於「系統設定 > SMTP 設定」提供自訂輸入介面，預設為 `FormosaBearV`。
  - 信件寄件者顯示名稱、郵件主旨前綴與郵件橫幅標題全面替換舊有寫死的 `Teedy`。
  - 具備智慧回退連動：優先使用 SMTP 寄件者名稱 ➔ 佈景主題系統名稱 (Theme Name) ➔ 預設 `FormosaBearV`。
- **郵件語系動態對應**：
  - 發送通知與密碼重設信件時，自動對應後台設定的系統預設語言（如繁體中文 `chi_tra` 自動對應為 `zh_TW` 語系，信件主旨為 `FormosaBearV - 請重新設置密碼`）。

### 🚀 免安裝可攜伺服器完善 (Portable Server Improvements)
- **終端訊息全面純英文化**：
  - 將 [start.bat](file:///W:/developer/project/github/teedydocs/scripts/templates/start.bat)、[stop.bat](file:///W:/developer/project/github/teedydocs/scripts/templates/stop.bat)、[start.sh](file:///W:/developer/project/github/teedydocs/scripts/templates/start.sh)、[stop.sh](file:///W:/developer/project/github/teedydocs/scripts/templates/stop.sh) 終端輸出全面改為純 ASCII 英文，徹底根除 Windows CMD 在 Big5 / CP950 編碼下的亂碼與命令執行中斷問題。
- **Windows CMD 括號語法衝突修復 (`這個時候不應有 ...。`)**：
  - 移除批次檔 `if (...)` 區塊中未轉義的括號字元，防止 CMD 解析器誤判區塊閉合而引發語法中斷。
- **嚴格 Java 21+ 檢驗機制**：
  - 移除脆弱的系統目錄盲目猜測迴圈；嚴格依序檢查 `%JAVA_HOME%\bin\java.exe` 與系統 `PATH` 中的 Java 執行檔。
  - 精確正則匹配 Java 21 及更高版本；若未檢測到合規執行階段，顯示清晰的 Adoptium OpenJDK 21 官方下載連結與環境變數設定指引。
- **Jetty 12 模組預先配置**：
  - 打包腳本 [scripts/package-portable.ps1](file:///W:/developer/project/github/teedydocs/scripts/package-portable.ps1) 與 [package-portable.sh](file:///W:/developer/project/github/teedydocs/package-portable.sh) 於組裝階段自動初始化 `start.d/ee10-deploy.ini` 與 `start.d/http.ini`，解決 `ERROR : No enabled jetty modules found!` 啟動錯誤。
- **解壓目錄自動同步**：
  - 打包腳本在產出 `target/FormosaBearV-docs-2.03-portable.zip` 的同時，自動同步最新內容至 `target/FormosaBearV-docs-2.03-portable/`，免除本機測試需手動解壓的繁瑣步驟。

### 📦 專案版本與產出全面升級 (Project Versioning)
- Maven POM（根模組與 `docs-core`、`docs-web-common`、`docs-web`）全面升級至 `<version>2.03</version>`。
- 前端 `package.json` 與 `package-lock.json` 更新為 `"version": "2.0.3"`。
- 後端 `config.properties`、`AppResource.java`、`BackupService.java` 全面更新至 `2.03`。
- 專案說明文件全面同步（[README.md](file:///W:/developer/project/github/teedydocs/README.md)、[README.zh-TW.md](file:///W:/developer/project/github/teedydocs/README.zh-TW.md)、[STORAGE.md](file:///W:/developer/project/github/teedydocs/STORAGE.md)、[STORAGE.zh-TW.md](file:///W:/developer/project/github/teedydocs/STORAGE.zh-TW.md)、[README.txt](file:///W:/developer/project/github/teedydocs/scripts/templates/README.txt)）。

---

## [v2.02] - 2026-11-04

> **基準版本**: Git Tag `2.01` ➜ Git Tag `2.02` (`53cc6936`)

### ✨ 新增功能 (New Features)
- **完整系統備份與還原 (Full System Backup & Restore)**：
  - 以高效串流方式即時匯出標準 ZIP 備份檔（`FormosaBearV-docs_backup_YYYYMMDD_HHmmss.zip`），不佔用伺服器臨時硬碟空間。
  - 具備跨資料庫相容性的 ANSI SQL 資料庫匯出檔 (`dump.sql`)，支援 **H2 與 PostgreSQL** 無縫資料遷移。
  - 完整封裝實體檔案儲存區（`storage/` 與 `delete/` 目錄）與系統設定檔 (`config.json`)。
  - 具備交易復原機制 (Transactional Rollback)、Zip Slip 路徑穿越攻擊防護、高風險操作確認提示，以及還原後背景自動重建 Lucene 全文搜尋索引。
- **Office 文件多頁互動式預覽 (Multi-Page Office Document Previews)**：
  - 整合 Mozilla PDF.js 核心引擎，支援在瀏覽器中對 **PowerPoint 簡報 (`.pptx`)** 與 **Word 文件 (`.docx`, `.odt`)** 進行逐頁翻頁閱讀。
  - 背景自動產生高畫質多頁 PDF 預覽暫存 (`_pdf`)，秒開不延遲。
  - 預覽視窗內建防右鍵功能表鎖定、禁止拖曳與隱藏列印按鈕。
- **階層式標籤權限與中斷繼承 (Hierarchical Tag Permissions & Block Inheritance)**：
  - 支援子標籤自動向上繼承祖先標籤之所有讀取與寫入權限，並支援「中斷繼承」鎖定專屬權限邊界。
  - 標籤管理、標籤編輯與文件標籤彈窗全面導入**階層式樹狀下拉選擇器**，支援即時關鍵字過濾、完整路徑麵包屑與防止循環相依防呆。
- **全域禁止下載權限開關 (Hardened Download Suppression)**：
  - 系統層級禁止下載開關 (`canDownload()`)，全面貫穿文件卡片、表格清單、右鍵操作選單與標籤視圖，在維持線上逐頁閱讀的前提下徹底防堵檔案外流。
- **免 Docker 綠色免安裝可攜版打包機制 (Standalone Portable Package Builder)**：
  - 提供 `package-portable.bat` 與 `package-portable.sh`，自動將 Jetty 12 與 WAR 打包為開箱即用的 `target/FormosaBearV-docs-2.02-portable.zip`。
  - 隔離儲存空間於本機 `data/` 目錄，內建五國語言（繁中、英、簡中、日、越）使用說明書 `README.txt`。
- **WAR 部署儲存持久化**：
  - 修正原版預設記憶體 H2 資料庫問題，確保每次 WAR 重新部署時資料庫永久寫入實體硬碟（Windows 位於 `%APPDATA%\FormosaBearV\Docs\db\docs.mv.db`，Linux 位於 `/var/docs/db/`）。

---

## [v2.01] - 2026-10-31

> **基準版本**: Teedy 1.12 ➜ `v2.01` (現代化架構重構基準版本)

### 🚀 現代化技術棧重構 (Modernization & Upgrades)
- **Java 21 (LTS)** 執行階段與編譯基準全面升級。
- **Eclipse Jetty 12.1.14 (`ee10`)** 與 **Jakarta Servlet 6.0** 容器支援。
- **Jersey 3.1.9** (Jakarta RESTful Web Services 3.1) 升級。
- **H2 Database 2.3.232** 升級，內建舊版 DDL 自動相容轉換機制 (`H2Dialect`)。
- 前端架構升級至 **AngularJS 1.8.3** 與 **jQuery 3.7.1**。
- **內建開源 Pan-CJK 字型 (`Noto Sans CJK TC`)**：開箱即用支援繁體中文、簡體中文、日文、韓文與英數排版渲染於 DOCX、PPTX、ODT 與 PDF 縮圖。
- **增強型標籤導航與管理介面**：支援樹狀可展開/收合層級導航與子資料夾/文件計數徽章。
- **Web 端儲存設定維護**：在系統設定中提供 `file.delete`、`file.duplicate` 與 `file.encrypt` 首次初始化鎖定管理。
