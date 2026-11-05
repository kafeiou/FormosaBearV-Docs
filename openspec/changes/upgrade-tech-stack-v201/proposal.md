# Proposal

## Why

本專案（命名為 `teedy-ng`）自 `https://github.com/fmaass/teedy-docs` 1.12 分支（Fork）出來並以 `v2.01` 作為全新起點。原專案使用的 Java 11、Jetty 11.0.20（已進入 EOL）、H2 1.4.199、AngularJS 1.6.6 與 jQuery 3.2.1 存在多項已知資安漏洞與過時依賴，且 `README.md` 仍殘留原專案推廣與過時資訊，亟需全面升級核心技術棧並重整專案文件。

## What Changes

- **BREAKING**: 將專案整體版本號由 `1.12-SNAPSHOT` 升級為 `2.01`（涵蓋 `pom.xml`、各子模組 `pom.xml`、`package.json` 及 `README.md`）。
- **BREAKING**: 後端編譯與執行環境由 Java 11 升級至 **Java 21 (LTS)**，並將 `maven-surefire-plugin` 升至 `3.2.5`。
- **BREAKING**: 應用伺服器與 Web 標準由 Jetty `11.0.20` (EE9 / Servlet 5.0) 升級至 **Jetty `12.1.14` (`ee10` / Jakarta Servlet `6.0.0`)**，同步將 Jersey 升級至 **`3.1.9`**，並更新 `web.xml`、`web-override.xml`、`docs.xml` 與 `Dockerfile`（同時修復 `Dockerfile` 第 16 行 `&& &&` 語法錯誤）。
- **BREAKING**: 嵌入式與測試資料庫 **H2** 由 `1.4.199` 升級至 **`2.3.232`**，`org.apache.directory.api` 升至 **`2.1.7`**；同步調整 `EMF.java`、`DialectUtil.java` 與測試 `hibernate.properties`，改用 `H2Dialect` 並自動轉換 H2 2.x 移除之舊版 SQL 語法（如 `SET IGNORECASE TRUE` 與 `bit default 0/1`）。
- 前端框架與函式庫升級：
  - 將官方核心 `angular.js`、`angular.sanitize.js`、`angular.touch.js` 由 `1.6.6` 升級至 **`1.8.3`**（消除 `angular.lowercase` 等棄用 API 呼叫並修補 XSS 漏洞）。
  - 將 `jquery.js` 由 `3.2.1` 升級至 **`3.7.1`**。
  - 將無客製 Patch 之第三方外掛升級至最終穩定版：`angular.translate.js`（含 `static-files-loader`）升至 **`2.19.1`**、`angular.ui-router.js` 升至 **`1.0.30`**、`angular.tmhDynamicLocale.js` 升至 **`0.1.38`**、`angular.ui-sortable.js` 升至 **`0.19.0`**（保留含有 Teedy 客製化程式碼的 `angular.ui-bootstrap.js`、`angular.file-upload.js` 與 Restangular 相依的 `underscore.js` 1.8.3）。
  - 補強分享頁面模組 `docs-web/src/main/webapp/src/app/share/app.js` 的 `$qProvider.errorOnUnhandledRejections(false)` 與語系 fallback 防呆機制。
  - 重構主導覽列與標籤瀏覽體驗：
    - 頂部導覽列改為 **「標籤 (`#/tag`)」、「文件管理 (`#/document`)」、「標籤管理 (`#/tagadmin`，僅管理權限者可見)」、「帳號 & 群組 (`#/user`)」**，並將登入後預設首頁設為「標籤 (`#/tag`)」。
    - 「標籤」與「標籤管理」左側樹狀結構支援 **節點展開 / 收合（Expand / Collapse）** 與全部展開/收合控制。
    - 「標籤 (`#/tag/:id`)」右側直接列出所選標籤關聯的所有文件卡片與附屬檔案縮圖/檔名，支援標籤內關鍵字篩選、排序、分頁、點擊檔案直接彈出預覽視窗 (`FileModalView`) 或下載、以及點擊文件標題進入檢視頁。
  - 改善快速上傳與文件檢視頁之檔案背景處理指示器與縮圖自動輪詢刷新機制，並移除舊版回饋按鈕與頁尾 Sismics 字樣。
- 後端內嵌開源全語系字型 `NotoSansCJKtc-Regular.otf`（支援繁中、簡中、日文、越南文、拉丁字母），修正 PPTX / DOCX / ODT 預覽縮圖亂碼與 `FileDao.update` 空指標問題。
- 全面改寫 `README.md`（英文）：明確標註本專案 Fork 自 `https://github.com/fmaass/teedy-docs` 1.12，移除所有與原專案（`sismics` / `teedy.io` / 贊助與舊版 Docker Hub）相關的連結與圖示，並更新為 `v2.01` 的環境需求與建置說明。

## Capabilities

### New Capabilities
- `runtime-and-server-platform`: 定義後端 Java 21、Jetty 12.1 (EE10)、Jakarta Servlet 6.0、Jersey 3.1.x、H2 2.3.x 資料庫方言轉換與容器化部署（Dockerfile / docs.xml）之平台規格。
- `frontend-angularjs-platform`: 定義前端 AngularJS 1.8.3、jQuery 3.7.1 與第三方外掛相容性及錯誤防呆規格。
- `project-versioning-and-docs`: 定義專案版本號 `2.01` 與 `README.md` Fork 來源聲明及去原專案化文件規格。

### Modified Capabilities

## Impact

- **後端與建置設定**：`pom.xml`、`docs-core/pom.xml`、`docs-web-common/pom.xml`、`docs-web/pom.xml`、`docs-web/src/main/webapp/WEB-INF/web.xml`、`docs-web/src/dev/main/webapp/web-override.xml`。
- **資料庫與 JPA 層**：`docs-core/src/main/java/com/sismics/util/jpa/EMF.java`、`docs-core/src/main/java/com/sismics/util/jpa/DialectUtil.java`、`docs-core/src/test/resources/hibernate.properties`、`docs-web/src/test/resources/hibernate.properties`。
- **前端資源與應用**：`docs-web/src/main/webapp/package.json`、`docs-web/src/main/webapp/src/lib/` 下各升級檔案、`docs-web/src/main/webapp/src/app/share/app.js`。
- **部署與文件**：`Dockerfile`、`docs.xml`、`README.md`。
