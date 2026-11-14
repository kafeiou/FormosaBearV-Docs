[ English ](CHANGELOG.md) | [ 繁體中文 ](CHANGELOG.zh-TW.md)

# FormosaBearV 版本更新紀錄 (Changelog)

本專案記錄自版本 `v2.01`（Teedy 現代化重構架構）以來的各版本更新歷程、安全性強化、架構改進與錯誤修復。

---

## [v2.06] - 2026-11-14

> **基準版本**: `v2.05` (`5559ea20`) ➜ `v2.06`

### ✨ 新增功能與系統改進 (New Features & System Improvements)
- **階層式標籤報表與歸檔匯出**：
  - 新增 [TagReportService.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/service/TagReportService.java)、[tag_report.ftl](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/resources/tag_report.ftl) 及 [TagResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/TagResource.java) 匯出端點（`GET /api/tag/{id}/export`、`/export/preview`），支援匯出 PDF、HTML、Excel（`.xlsx`）與 JSON ZIP 壓縮歸檔（ZIP 內附英文 `README.md`）。
  - 新增標籤匯出對話框（[ModalTagExport.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/tag/ModalTagExport.js)、[tag.export.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/tag.export.html)），支援選擇英文（`en`）、繁體中文（`zh_TW`）或簡體中文（`zh_CN`）報表語系。
- **公開分享匯出控制與文件 PDF 匯出權限控管 (DB v40)**：
  - 透過 [dbupdate-040-0.sql](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/resources/db/update/dbupdate-040-0.sql) 於 `T_SHARE` 新增 `SHA_ALLOW_EXPORT_B`（`allow_export`）欄位，並整合至 [ShareResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/ShareResource.java)、[DocumentModalShare.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/document/DocumentModalShare.js) 與 [share.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/share/share.html)。
  - 於 [DocumentResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/DocumentResource.java)（`GET /api/document/{id}/pdf`）與 [FileResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/FileResource.java) 強制檢查下載／匯出授權。
- **LDAP 支援 STARTTLS / LDAPS 協定與連線測試功能 (DB v41)**（上游 Issue [sismics/docs#788](https://github.com/sismics/docs/issues/788)、[sismics/docs#789](https://github.com/sismics/docs/issues/789)，感謝 [@Strongleong](https://github.com/Strongleong) 與 [@Iweisc](https://github.com/Iweisc)）：
  - 透過 [dbupdate-041-0.sql](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/resources/db/update/dbupdate-041-0.sql) 於 `T_LDAP_CONFIG` 新增 `LDC_PROTOCOL_C` 欄位，並更新 [LdapAuthenticationHandler.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/LdapAuthenticationHandler.java) 支援 `LDAP`、`LDAPS` 與 `STARTTLS`。
  - 於 [AppResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/AppResource.java) 新增 `POST /api/app/config_ldap/test` 端點，並於 [SettingsLdap.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/settings/SettingsLdap.js) / [settings.ldap.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.ldap.html) 提供一鍵連線測試按鈕。
- **可攜版打包腳本版本自動偵測**：
  - 更新 [package-portable.ps1](file:///W:/developer/project/github/FormosaBearV-Docs/scripts/package-portable.ps1) 自動從根目錄 `pom.xml` 讀取 `<version>`，不再寫死版本號。

### 🐛 錯誤修復 (Bug Fixes)
- **修復 PostgreSQL 環境啟用 `HeaderBasedSecurityFilter` 時因 `null` Token 導致 `/api/user` 崩潰問題**（上游 Issue [sismics/docs#786](https://github.com/sismics/docs/issues/786)，感謝 [@fmaass](https://github.com/fmaass) 回報）：
  - 於 [AuthenticationTokenDao.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/dao/AuthenticationTokenDao.java) 之 `updateLastConnectionDate(String id)` 與 `deleteByUserId(String userId, String id)` 加入 `null`／空字串防呆檢查，避免在 PostgreSQL 執行原生 SQL 時傳入未定型 `null`（`bytea`）參數引發 `PSQLException: ERROR: operator does not exist: character varying = bytea`。
  - 於 [UserResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/UserResource.java) 的 `GET /api/user` 與 `DELETE /api/user/session` 補上檢查：當請求未攜帶 `auth_token` Cookie（例如使用 `HeaderBasedSecurityFilter` 驗證）時，跳過 `updateLastConnectionDate` 並改呼叫單參數 `deleteByUserId(principal.getId())`。
  - 於 [TestAuthenticationTokenDao.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/test/java/com/sismics/docs/core/dao/TestAuthenticationTokenDao.java) 與 [TestSecurity.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/test/java/com/sismics/docs/rest/TestSecurity.java) 補上對應單元測試與 REST 迴歸測試。
- **修復文件日期搜尋條件之客戶端時區偏移問題**（上游 Issue [sismics/docs#803](https://github.com/sismics/docs/issues/803)）：
  - 更新 [DocumentSearchCriteriaUtil.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/util/DocumentSearchCriteriaUtil.java) 與 [Document.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/document/Document.js)，於 `at:` / `after:` / `before:` 日期篩選條件中套用客戶端時區偏移量（`tz_offset`）。
- **修復 PostgreSQL 環境下 `/api/tag/list` `SELECT DISTINCT` 排序語法錯誤**：
  - 於 [TagDao.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/dao/TagDao.java) 的 `findByCriteria` 中，將 `coalesce(t.TAG_ORDER_N, 999999999) as c8` 補入 `SELECT DISTINCT` 清單，修復 PostgreSQL 執行標籤列表查詢時報錯 `ERROR: for SELECT DISTINCT, ORDER BY expressions must appear in select list`（連帶導致前端標籤管理無法顯示新增標籤區塊）的問題。

---

## [v2.05] - 2026-11-12

> **基準版本**: `v2.04` (`3462d002`) ➜ `v2.05`

### ✨ 新增功能與系統改進 (New Features & System Improvements)
- **多組 LDAP 企業目錄驗證與優先順序自動備援 (`T_LDAP_CONFIG`, DB v39)**：
  - 透過 [dbupdate-039-0.sql](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/resources/db/update/dbupdate-039-0.sql) 建立 `T_LDAP_CONFIG` 資料表，並新增 [LdapConfig.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/model/jpa/LdapConfig.java) 實體與 [LdapConfigDao.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/dao/LdapConfigDao.java)；升級時自動將 `T_CONFIG` 既有單一 LDAP 設定無縫遷移至 `T_LDAP_CONFIG`，同時納入 [BackupService.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/service/BackupService.java) 完整系統備份與還原流程。
  - 重構 [LdapAuthenticationHandler.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/LdapAuthenticationHandler.java) 支援依優先順序（`LDC_ORDER_N`）逐一對已啟用的 LDAP 伺服器進行連線與驗證；當特定伺服器查無帳號、密碼不符或連線逾時，自動切換至下一順位的 LDAP 伺服器繼續嘗試，同時完整保留 RFC 4515 特殊字元轉義防護與停用帳號攔截機制。
  - 擴充 [AppResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/AppResource.java) 之 `GET /api/app/config_ldap` 與 `POST /api/app/config_ldap`，支援多組伺服器陣列（`servers` / `servers_json`）讀寫，並向下相容舊版單一伺服器表單參數。
  - 全新設計「設定 > LDAP 驗證」管理介面（[settings.ldap.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.ldap.html)、[SettingsLdap.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/settings/SettingsLdap.js)），支援新增、編輯、獨立啟用／停用、上下調整驗證優先順序（`#1`、`#2`…）及刪除多組 LDAP 伺服器。
- **文件線上預覽動態浮水印覆蓋層與可配置英文日期格式 (`PREVIEW_WATERMARK_ENABLED` & `PREVIEW_WATERMARK_DATE_FORMAT`)**：
  - 於 [ConfigType.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/constant/ConfigType.java) 與 [ConfigUtil.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/ConfigUtil.java) 新增浮水印啟用開關與日期格式設定，並於 [AppResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/AppResource.java) 擴充 `GET /api/app` 回傳欄位與 `POST /api/app/config_watermark` 管理端點。
  - 於內部預覽與外部公開分享預覽頁面（[FileModalView.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/document/FileModalView.js)、[file.view.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/file.view.html)）的 PDF 互動式逐頁畫布與圖片預覽上方，疊加不阻擋滑鼠操作（`pointer-events: none`）的半透明斜向重複平鋪 SVG 浮水印覆蓋層。
  - 浮水印內容自動顯示「當前登入使用者帳號 + 英文格式日期」（未登入之公開分享頁面顯示 `Guest`），無論瀏覽器語系為何，日期與月份名稱一律強制以英文（`en-US`）格式化輸出；並於「設定 > 一般設定」（[settings.config.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.config.html)、[SettingsConfig.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/settings/SettingsConfig.js)）提供開關切換、常用英文日期格式快速套用按鈕（如 `dd MMM yyyy`、`MMM dd, yyyy`、`yyyy-MM-dd`、`dd MMMM yyyy HH:mm`）與即時預覽。
- **新增日文（`ja`）與越南文（`vi`）完整語系支援**：
  - 新增日文（[ja.json](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/ja.json)、[angular-locale_ja.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/angular-locale_ja.js)）與越南文（[vi.json](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/vi.json)、[angular-locale_vi.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/angular-locale_vi.js)）全翻譯語系包，達成 100% 翻譯鍵值覆蓋（共 723 個鍵值，零缺漏）。
  - 於前端主程式與公開分享頁面（[app.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/app.js)、[share/app.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/share/app.js)）註冊 `ja` 與 `vi` 可用語系鍵及瀏覽器語系自動偵測對應規則。
  - 於登入頁面與分享頁面（[index.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/index.html)、[share.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/share.html)）之語言切換選單新增「日本語」與「Tiếng Việt」。
  - 於 [angular.timeago.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/lib/angular.timeago.js) 擴充日文與越南文相對時間字串對應字典。
- **頁尾超連結品牌網址與台灣玉山登入背景圖片更新**：
  - 更新全數 14 個語系檔中的 `footer_sismics` 屬性，超連結網址全面替換為 `https://kafeiou.pw`。
  - 將預設登入頁面背景圖片（[background.jpg](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/resources/image/background.jpg)）替換為台灣玉山實景風景照（CC0 1.0 公眾領域授權）。
- **維護與專案清理**：
  - 移除已無使用的快照建置產物 `build/docs-NoFileEncrypt_1.12-SNAPSHOT.war`。

### 🐛 錯誤修復 (Bug Fixes)
- **個人帳號與使用者管理密碼修改修復**：
  - 修復 [SettingsAccount.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/settings/SettingsAccount.js) 未初始化 `$scope.user = {}`，導致表單被 `ng-if` 包裹時建立獨立子作用域，向 `POST /api/user` 發送空封包並造成後端未實際寫入新密碼（但仍回傳成功）的重大問題。
  - 於個人帳號設定（[settings.account.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.account.html)）與使用者編輯（[settings.user.edit.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.user.edit.html)）表單補齊 `ng-submit`，支援在密碼輸入框按下 Enter 鍵直接觸發送出。
  - 補齊儲存成功後的表單狀態重設（`$setPristine()`）與後端錯誤回傳提示，消除輸入框變紅誤導並確保操作反饋明確。

---

## [v2.04] - 2026-11-11

> **基準版本**: `v2.03` (`1a8bd912`) ➜ `v2.04`

### ✨ 新增功能與系統改進 (New Features & System Improvements)
- **文件下載權限管控矩陣 (`DOC_ALLOW_DOWNLOAD_B`, DB v37)** (`ea6d5ba9`, `95d3d14c`)：
  - 透過 [dbupdate-037-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-037-0.sql) 於 `T_DOCUMENT` 新增 `DOC_ALLOW_DOWNLOAD_B` 欄位（預設 `TRUE`）。
  - 支援文件建立者、具備文件編輯權限者、具備該文件所掛載任一標籤編輯權限者以及 `admin` 獨立切換每份文件的「允許下載檔案」開關。
  - 於單檔下載（`GET /api/file/{id}/data` 未帶 `size` 參數）、批次打包下載（`GET /api/file/zip`）與前端介面（`canDownload`）全面實施統一的下載授權矩陣，防止未獲授權之唯讀使用者外流原始檔案，同時完整保留線上翻頁預覽與縮圖檢視能力。
- **員工離職帳號停用安全防護與資產／權限一鍵交接 (`POST /api/user/{username}/transfer`)**：
  - **停用即時撤銷連線與登入阻擋**：停用帳號時立即清除該帳號所有登入 Token（`AuthenticationTokenDao.deleteByUserId`），並於 [TokenBasedSecurityFilter.java](file:///W:/developer/project/github/teedydocs/docs-web-common/src/main/java/com/sismics/util/filter/TokenBasedSecurityFilter.java) 及 [LdapAuthenticationHandler.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/LdapAuthenticationHandler.java) 攔截已停用帳號，同時自 ACL 權限指派搜尋清單中自動隱藏（`UserCriteria.active`）。
  - **名下資產統計與防誤刪保護**：`GET /api/user/{username}` 回傳使用者名下文件數（`owned_document_count`）、標籤數（`owned_tag_count`）與檔案數（`owned_file_count`）；若名下仍有資產則禁止直接刪除帳號（回傳 `UserHasAssets`），並鎖定前端刪除按鈕以防止誤刪。
  - **資產與 ACL 權限一鍵交接**：於使用者管理頁面提供一鍵移轉功能，將原帳號名下所有文件（`DocumentDao.transferOwnership`）、標籤（`TagDao.transferOwnership`）、實體加密檔案與配額統計（`FileUtil.transferFileStorage`、`FileDao.transferOwnership`）、待簽核工作流步驟（`RouteStepDao.transferActiveSteps`），以及**所有獲授之 ACL 存取／編輯權限**（`AclDao.copyUserAcls`，包含由 `admin` 或他人授予的標籤與文件權限）一併移轉與複製給接手人。
- **自訂檔案版本標記 (`FIL_VERSION_LABEL_C`, DB v38) 與歷史版本介面強化**：
  - 透過 [dbupdate-038-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-038-0.sql) 於 `T_FILE` 新增 `FIL_VERSION_LABEL_C varchar(50)` 欄位；既有檔案自動保留 `1.0`、`2.0` 標記，新上傳檔案與新版本預設為空白（`null`），讓使用者可自由填入版本文字（如 `001`、`v1.2-draft`、`Rev.A`）。
  - 網格／標籤卡片檢視僅在有填寫版本標記時顯示 `({{ file.version_label }})`，清單檢視直接顯示 `{{ file.version_label }}`，未填寫時保持留白。
  - 重新設計歷史版本對話框（[file.versions.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/file.versions.html)、[ModalFileVersions.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/controller/ModalFileVersions.js)）：改由最新至最舊排序（`orderBy: '-version'`），新增 `#序號`、「目前版本」徽章、精確至秒的完整時間（`yyyy-MM-dd HH:mm:ss`），以及列內即時修改版本標記與儲存按鈕。
  - 於「設定 > 我的帳號」（[settings.account.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/settings.account.html)）與「設定 > 一般設定」（[settings.config.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/settings.config.html)）新增歷史版本「類型」與「建立日期」欄位顯示開關（預設隱藏，可隨時開啟）。
- **無上限個人儲存配額 (`0` = 無上限)** (`ea6d5ba9`)：
  - 更新 [FileUtil.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/FileUtil.java) 與使用者管理介面，支援將儲存空間配額設為 `0` 代表無容量限制。
- **全域標籤建立者懸停提示 (`getTagTooltip`)**：
  - 於 [app.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/app.js) 新增全域 `$rootScope.getTagTooltip(tag)` 並綁定至 [SelectTag.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/directive/SelectTag.js)，在標籤管理、標籤檢視、文件列表／內頁及標籤選擇器懸停時皆會顯示 `建立者：<帳號>`。
- **權限指派 (`<acl-edit>`) 下拉選單**：
  - 升級 [directive.acledit.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/directive.acledit.html) 與 [AclEdit.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/directive/AclEdit.js)，支援零字元觸發候選清單（`typeahead-min-length="0"`）與下拉展開按鈕，指派標籤、文件或審核流程權限時可直接點選人員與群組。

### 🐛 問題修復 (Bug Fixes)
- **修復新增「可編輯 (`READWRITE`)」直接權限漏寫問題**：於 [AclResource.java](file:///W:/developer/project/github/teedydocs/docs-web/src/main/java/com/sismics/docs/rest/resource/AclResource.java) 改用 [AclDao.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/dao/AclDao.java) 之 `hasDirectAcl` 檢查，確保前端同時送出 `READ` 與 `WRITE` 時不會因既有繼承或建立者權限而略過直接 ACL 紀錄。
- **修復儲存配額單位重複換算與面板跳離問題**：修正使用者管理頁面重新載入帳號時 Byte/MB 重複相除的問題，並於儲存使用者或群組後保持編輯面板開啟。
- **修復輸入框群組按鈕文字無法顯示問題**：修正 [main.less](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/style/main.less) 中 `.input-group-btn > .btn` 因繼承 Bootstrap `font-size: 0` 與 `1em` 導致按鈕文字變為 `0px` 的問題。

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
- **SMTP 電子郵件傳輸強化與即時測試工具 (SMTP Enhancements & Test Tool)**：
  - **多元傳輸加密模式**：新增 `ConfigType.SMTP_SECURITY`，提供 `AUTO`（智慧自動）、`STARTTLS`、`SSL/TLS` 與 `NONE`（明文純文字）四種連線協定，相容各類公私有郵件伺服器。
  - **本機/內網無認證轉發 (Anonymous Relay)**：完整支援本機或企業內部無帳號密碼的 SMTP Relay（如 `localhost:25`、Postfix 等），留空帳號與密碼時系統自動略過憑證驗證，實現免帳密發信。
  - **後台即時發信測試工具**：在「系統設定 > SMTP 設定」新增收件信箱輸入欄位與【發送測試信】按鈕（呼叫 `POST /api/app/test_smtp`），可在儲存前即時測試 SMTP 連線、TLS 握手與郵件發送，並於畫面即時顯示成功提示或詳細錯誤訊息（包含 Socket 連線逾時與伺服器拒絕原因）。
- **全系統 12 國語言在地化文字完整補全 (Exhaustive Localization Fixes)**：
  - 徹底清查並補齊 12 國語系檔案中遺漏之 9 處關鍵翻譯鍵值（涵蓋 LDAP 狀態提示、忘記密碼錯誤訊息、帳號轉換內部認證，以及 SMTP 測試工具相關字串）。
  - 徹底修復 LDAP 使用者於帳號設定出現 `settings.account.ldap_title`、`settings.account.ldap_desc` 與忘記密碼時出現 `login.password_lost_ldap_forbidden` 之未翻譯代碼問題。
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

### 🔒 相依套件安全性全面升級 (Security Vulnerability Upgrades)
- **Log4j 1.x 終止維護漏洞修復**：
  - 將專案中之 `log4j:log4j:1.2.17` 全面替換為 `ch.qos.reload4j:reload4j:1.2.25`，並將 SLF4J 橋接器升級為 `org.slf4j:slf4j-reload4j:1.7.36`。
  - 徹底消除 Log4j 1.2.17 累積之高風險 CVE 漏洞（包含 CVE-2019-17571 SocketServer RCE、CVE-2022-23307 Chainsaw、CVE-2022-23305 JDBCAppender、CVE-2022-23302 JMSSink、CVE-2021-4104 JMSAppender）。
  - 於根 `pom.xml` 中為 `subethasmtp-wiser` 增加排除條件，杜絕間接引入之 `commons-logging` 與 `log4j:1.2.12`，保證依賴樹乾淨。
- **後端核心驅動與框架升級**：
  - **Jsoup HTML 解析器**：由 `1.15.4` 升級至 `1.23.2`，修復屬性與 XSS 繞過漏洞。
  - **Apache Commons Compress**：由 `1.22` 升級至 `1.27.1`，修復 CVE-2024-26308、CVE-2024-25710、CVE-2023-42503 解壓縮 OOM 與無限迴圈 DoS。
  - **Commons IO**：由 `2.11.0` 升級至 `2.18.0`，修復 CVE-2024-47554 未受控資源消耗。
  - **Google Guava**：由 `31.1-jre` 升級至 `33.3.1-jre`，修復 CVE-2023-2976 不安全暫存目錄建立。
  - **Apache PDFBox**：由 `2.0.29` 升級至 `2.0.32`，修復 CVE-2024-34750 異常 PDF 解析 DoS。
  - **FreeMarker 範本引擎**：由 `2.3.32` 升級至 `2.3.34`，修復 CVE-2024-36779 範本注入與任意檔案讀取。
  - **Square OkHttp**：由 `4.10.0` 升級至 `4.12.0`，修復 CVE-2023-3782 憑證驗證繞過。
  - **Bouncy Castle 加密庫**：由 `bcprov-jdk15on:1.70` 全面遷移至 `bcprov-jdk18on:1.78.1`，修復 CVE-2024-29857 RSA 解析 DoS 與 CVE-2023-33201 LDAP 注入。
  - **PostgreSQL JDBC 驅動**：由 `42.6.0` 升級至 `42.7.11`。
  - **Apache Directory LDAP Client API**：由 `2.1.7` 升級至 `2.1.8`。
  - **Jersey REST Client**：由 `3.1.9` 升級至 `3.1.10`（連帶同步 Jersey 全套核心元件）。
- **前端建置工具鏈現代化與舊版命令列工具退場 (Frontend Toolchain & Deprecation)**：
  - **移除已廢棄之獨立上傳工具 `docs-importer`**：
    - 徹底移除無官方維護且依賴已封存套件 `request: ^2.83.0` 之舊版獨立 Node.js CLI 工具 `docs-importer/`。
    - 同步清理前端 UI 路由與設定選單（`settings.html`、`app.js`、`settings.fileimporter.html`）以及 11 國語言檔中之無效鍵值，消除 12 項深層無法獨立修復之歷史相依漏洞（包含 `boom`, `cryptiles`, `hawk`, `sntp`, `har-validator` 等）。
  - **前端建置核心與相依套件升級 (`docs-web`)**：
    - 將 `grunt` 自 `^1.0.1` 升級至 `^1.6.1`，修復 CVE-2020-7729（任意程式碼執行）與 CVE-2022-0436（競爭條件/任意檔案寫入）。
    - 透過 `package.json` 之 `overrides` 機制安全鎖定間接相依版本，消除高風險弱點：
      - `async`：強制升級至 `^2.6.4`，修復 CVE-2021-43138 原型污染漏洞。
      -       - `js-yaml`：強制升級至 `^3.14.1`，修復 CVE-2020-7712 原型污染漏洞。
      - `hosted-git-info`：強制升級至 `^2.8.9`，修復 CVE-2021-23362 ReDoS 弱點。
      - `underscore.string`：強制升級至 `^3.3.6`。
      - `uuid`：強制升級至 `^9.0.1`。
      - `lodash`：升級至 `4.18.1`（修復 CVE-2021-23337 命令注入與原型污染）。
      - `semver`：升級至 `5.7.3`（修復 CVE-2022-25883 ReDoS 漏洞）。
      - `qs`：升級至 `6.17.1`（修復 CVE-2022-24999 原型污染）。
      - `tough-cookie`：升級至 `4.1.4`（修復 CVE-2023-26136 原型污染）。
      - `form-data`：升級至 `4.0.6`（修復 CVE-2020-7608 標頭注入與表單安全）。
      - `minimist`：升級至 `1.2.8`（修復 CVE-2021-44906）。
      - `getobject`：升級至 `1.1.1`（修復 CVE-2020-28282）。
      - `underscore`：升級至 `1.13.8`（修復 CVE-2021-23358）。
      - `json-schema`：升級至 `0.4.0`（修復 CVE-2021-3918）。

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
