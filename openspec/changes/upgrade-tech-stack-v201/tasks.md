# Tasks

## 1. 專案版本號與後端 Maven / Servlet 平台升級

- [x] 1.1 更新 `pom.xml`、`docs-core/pom.xml`、`docs-web-common/pom.xml`、`docs-web/pom.xml` 的專案版本號為 `2.01`，並確認父子模組版本引用一致
- [x] 1.2 更新根目錄 `pom.xml`：將 `maven.compiler.source` 與 `maven.compiler.target` 升至 `21`、`jakarta.servlet-api` 升至 `6.0.0`、`jersey` 升至 `3.1.9`、Jetty 升至 `12.1.14` (`org.eclipse.jetty.ee10` 模組)、`h2` 升至 `2.3.232`、`org.apache.directory.api` 升至 `2.1.7`、`maven-surefire-plugin` 升至 `3.2.5`
- [x] 1.3 更新 `docs-web/pom.xml` 開發環境 Profile 的 `jetty-maven-plugin` 為 `org.eclipse.jetty.ee10:jetty-ee10-maven-plugin`
- [x] 1.4 更新 `docs-web/src/main/webapp/WEB-INF/web.xml` 與 `docs-web/src/dev/main/webapp/web-override.xml` 之 Schema 為 Jakarta Servlet `6.0` (`web-app_6_0.xsd`)

## 2. H2 2.3.x 資料庫方言與 SQL 轉換適配

- [x] 2.1 更新 `docs-core/src/main/java/com/sismics/util/jpa/EMF.java`、`docs-core/src/test/resources/hibernate.properties` 與 `docs-web/src/test/resources/hibernate.properties`，將 H2 方言改為 `org.hibernate.dialect.H2Dialect` 並在 H2 JDBC URL 加入 `IGNORECASE=TRUE;MODE=LEGACY;NON_KEYWORDS=VALUE,USER`
- [x] 2.2 更新 `docs-core/src/main/java/com/sismics/util/jpa/DialectUtil.java`，新增 H2 2.x 語法轉換邏輯（處理 `SET IGNORECASE TRUE`、`(cached|memory) table`、`datetime`、`longvarchar` 與 `bit default 0/1` 等語法），並檢查 DAO 層原生 SQL 在 H2 2.x 下的相容性
- [x] 2.3 執行 `mvn test-compile` 與 `docs-core` 單元測試，驗證 Java 21 與 H2 2.3.232 資料庫初始化及測試全數通過

## 3. 前端 AngularJS 1.8.3、jQuery 3.7.1 與第三方外掛升級

- [x] 3.1 更新 `docs-web/src/main/webapp/package.json` 版本號為 `2.01`
- [x] 3.2 升級 `docs-web/src/main/webapp/src/lib/` 下之 `angular.js`、`angular.sanitize.js`、`angular.touch.js` 至 `1.8.3`，以及 `jquery.js` 至 `3.7.1`，並確認檔案標頭版本正確
- [x] 3.3 升級 `docs-web/src/main/webapp/src/lib/` 下之無客製 Patch 外掛：`angular.translate.js`（合併 `angular-translate` 與 `angular-translate-loader-static-files` `2.19.1`）、`angular.ui-router.js` (`1.0.30`)、`angular.tmhDynamicLocale.js` (`0.1.38`)、`angular.ui-sortable.js` (`0.19.0`)
- [x] 3.4 更新 `docs-web/src/main/webapp/src/app/share/app.js`，加入 `$qProvider.errorOnUnhandledRejections(false)` 與預設語系退回 (`fallback`) 防呆機制，並驗證 Grunt 打包壓縮 (`uglify`) 可正常執行

## 4. 容器部署設定與 README.md 重寫及整合測試驗證

- [x] 4.1 更新 `Dockerfile`（改用 OpenJDK 21、Jetty `12.1.14`、`ee10-webapp,ee10-deploy` 模組並修正第 16 行 `&& &&` 語法錯誤）與 `docs.xml`（改用 `org.eclipse.jetty.ee10.webapp.WebAppContext`）
- [x] 4.2 以英文全面改寫 `README.md`，標註專案 Fork 自 `https://github.com/fmaass/teedy-docs` 1.12、版本自 `2.01` 開始，移除所有原專案 (`sismics` / `teedy.io`) 相關連結與圖示，並更新為 Java 21 與 Jetty 12.1 的建置與運行說明
- [x] 4.3 執行完整 Maven 測試與打包驗證，確保所有模組在 Java 21、Jetty 12.1 (EE10)、Jersey 3.1.9 與 H2 2.3.232 下建置成功

## Workflow follow-up

- 待所有任務實作與測試驗證完成後，執行 OpenSpec 變更歸檔 (`openspec archive`) 並自動重新生成 `openspec/specs/README.md`。
