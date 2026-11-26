[ English ](CHANGELOG.md) | [ 繁體中文 ](CHANGELOG.zh-TW.md)

# FormosaBearV 版本更新紀錄 (Changelog)

本專案記錄自版本 `v2.01`（Teedy 現代化重構架構）以來的各版本更新歷程、安全性強化、架構改進與錯誤修復。

---

## [v3.02-SNAPSHOT]（未發布）

### 新增功能 (Added)
- **重複檔名上傳定位視窗與防卡死復原（`FileResource.java`、`file.duplicate.modal.html`、`DocumentEdit.js`、`DocumentViewContent.js`）**：
  - HTTP 403 `FileNameDuplicate` 回應新增回傳 `file_name` 與 `duplicates`（含 `file_id`、`file_name`、`document_id`、`document_title`、`tags`、`create_date`），前端於 16 國語系下彈出視窗顯示完整重複檔名、標籤與可點擊開啟之既有文件連結。
  - 改善上傳失敗後的互動流程：多檔批次上傳遇單一失敗會繼續上傳其餘合法檔案；新增文件頁於關閉重複視窗後自動清空 `$scope.newFiles` 並跳轉進入剛建立的文件（避免停留原表單誤建重複文件）；文件檢視頁失敗時自動移除失效的佔位進度卡片。
- **依標籤階層結構一鍵打包下載 ZIP（含歷史版本選項與清單，`GET /api/tag/{id}/zip`、`#/tag/:id`）**：
  - 於標籤瀏覽頁頂部標題列新增「**打包下載 ZIP**」下拉選單（同步移除旁側易造成誤導的單層文件數顯示），提供「**僅下載最新版**」（預設）與「**含歷史版本**」（`?include_versions=true`），由 `FileDao.getAllVersionsByDocumentsIds`、`TagZipExportService` 與 `TagResource.downloadTagZip` 提供支援。
  - 以純唯讀串流方式將所選標籤與所有可讀子標籤下允許下載的檔案，依 `標籤/子標籤/文件標題/檔名` 目錄結構打包為 UTF-8 ZIP（相容未加密與加密模式、嚴格遵守全域下載總開關與文件個別 `allow_download` 權限，並完整保留標籤／文件／檔案自訂順序）；僅有單一版本時維持原檔名，含多版本時同層命名為 `檔名_#1_1.0.副檔名`（有設定版本標籤）或 `檔名_#2.副檔名`（未設定版本標籤）。
  - 根目錄固定附帶 UTF-8 BOM 的 `_manifest.txt`，包含匯出摘要標頭與等寬對齊的 `[O]` / `[X]` 完整清單（含禁止下載或遺失而略過之可讀檔案），同步補齊 16 國語系與 `TestTagResource` 整合測試。
- **資料一致性檢查與選擇性清理（`GET /api/app/integrity/scan`、`POST /api/app/integrity/clean`、`#/settings/monitoring`）**：
  - 於「**設定 → 監控**」新增限管理員使用的兩階段「**先掃描預覽 → 勾選確認 → 徹底清理**」工具（`StorageIntegrityService`、`AppResource`、`settings.monitoring.html`、`SettingsMonitoring.js`），可偵測三類不匹配項目：**孤兒歷史版本**（`latest_version = false` 且已無有效最新版）、**缺實體檔的資料庫紀錄**（最新版預設不勾選並顯示警示）、以及 **`storage/` 磁碟孤兒殘留檔**。
  - 選擇性清理會徹底移除勾選的資料庫異常紀錄（軟刪除、移出 Lucene 索引、刪除殘留衍生檔與校正配額，不進垃圾桶）並具路徑遍歷防呆（CWE-22）清理磁碟孤兒檔；同步更新 `RebuildIndexAsyncListener.reprocessOcr()` 跳過非最新版歷史紀錄，並補齊 16 國語系與 `TestAppResource` 整合測試。
- **標籤區同畫面雙分頁文件檢視與編輯（`#/tag/:tagId/document/:id`）**：
  - 於標籤瀏覽區新增 `tag.document` 巢狀子路由（`.view`、`.content`、`.content.file`、`.workflow`、`.permissions`、`.activity`、`.edit`）與右側頂部雙分頁（「**文件列表**」｜「**文件檢視與檔案**」），點選文件標題或「開啟」預設直接於右側檢視文件，全程保留左側標籤樹高亮與 `?focus` 聚焦模式。
  - 切回「文件列表」時保留同標籤下最後檢視的文件分頁以便來回切換，切換至左側其他標籤時自動清除記憶；編輯、儲存、取消、刪除、檔案預覽彈窗及檔案上傳／刪除皆停留於 `#/tag` 內不跳離，並補齊 16 國語系翻譯。
- **標籤管理同畫面文件檢視／編輯、書本圖示配色與建立者提示（`#/tagadmin`）**：
  - 於 `#/tagadmin/document/:id` 右側頂部新增「**標籤管理**」與「**文件檢視與檔案**」雙分頁及巢狀子路由（`tagadmin.document.view.*`、`tagadmin.document.edit`），可在不跳離標籤管理頁且不收合左側標籤樹的情況下直接檢視文件、開啟檔案預覽彈窗、操作簽核／權限／活動與編輯文件；點選左側其他文件時自動維持目前所處模式（`getTagAdminDocHref`），編輯儲存或刪除後同步刷新快速貼標頁籤與左側樹狀列表。
  - 左側標籤樹的文件改以書本圖示呈現（`fas fa-book`，淺色預設 `#8b5a2b`、深色預設 `#d4a373`），並於「**設定 → 個人帳號**」（`#/settings/account`）新增淺色／深色獨立調色盤、即時預覽與重設按鈕（儲存於 `localStorage`）。
  - `GET /api/tag/{id}/documents` 與 `GET /api/tag/untagged/documents` 新增回傳 `creator` 與 `file_count` 欄位（同步補齊 `TestTagResource` 整合測試），左側標籤樹內的一般文件與未貼標籤文件皆於標題後方以括號顯示檔案數（`標題 (N)`），滑鼠移至文件時顯示建立者提示，並完成全部 16 國語系翻譯。
- **標籤管理樹狀文件檢視、快速勾選貼標與樹內排序（`#/tagadmin`）**：
  - 左側標籤樹新增「**顯示／隱藏文件**」開關（預設開啟）、展開標籤時延遲載入直接關聯文件（`GET /api/tag/{id}/documents`，每批 20 筆＋載入更多）、`GET /api/tag/list` 回傳 `direct_document_count`、樹內文件 `▲ / ▼` 上下排序（與 `POST /api/tag/{id}/document/reorder` 及標籤瀏覽頁完全同步），並於樹狀最下方新增「**未貼標籤的文件**」虛擬資料夾（`GET /api/tag/untagged/documents`）。
  - 點選左樹任一文件即於右側開啟「**快速管理文件標籤**」面板（`#/tagadmin/document/:id`），提供已勾選標籤徽章與可搜尋之階層標籤勾選樹（自動排除系統隱藏標籤）；按下儲存呼叫新增的專屬端點 `POST /api/document/{id}/tags`（僅更新標籤並自動保留既有系統隱藏標籤，儲存後同步刷新左樹），補齊 16 國語系與 `TestTagResource` 整合測試。
- **Apache Lucene 升級至 `10.6.0` 與索引重建維護鎖定**：
  - 將 Apache Lucene 由 `8.7.0` 升級至 **`10.6.0`**（原生支援 JDK 21；依賴更名為 `lucene-analysis-common`、`RAMDirectory` 改用 `ByteBuffersDirectory`、`IndexSearcher.doc(...)` 改用 `storedFields().document(...)`，並於開機自動清理舊版 Lucene 8 索引前先安全關閉檔案控制代碼以避開 Windows 鎖檔）。
  - 新增雙層索引重建維護鎖定機制：開機自動升級重建期間全面阻擋所有登入與既有連線（`503 ReindexingInProgress`），管理員手動重建期間僅阻擋一般使用者與訪客並放行管理員檢視監控進度。
  - `GET /api/app` 回傳不含敏感檔名之公開重建進度（`running`、`startup_maintenance`、`percent`、`processed`、`total`），前端加入 16 國語系全域維護遮罩與登入頁進度條，每 3 秒自動輪詢並於完成後自動恢復正常。
- **已分享連結總覽（`GET /api/share/list`、`#/settings/share`）**：
  - 於「**設定 → 個人設定 → 已分享連結**」新增分享總覽頁，所有非訪客使用者皆可檢視、篩選、一鍵複製、開啟文件、即時切換允許下載／僅限預覽與撤銷所有自己具備 `WRITE` 權限文件上的有效分享連結（包含離職移轉接手的文件）。
  - 管理員可於同頁切換「**我的分享／全部使用者的分享**」（`scope=all`），稽核並直接管理全站所有使用者的分享連結（`POST` / `DELETE /api/share/{id}` 同步開放 `ADMIN` 權限）。
  - 補齊 16 國語系與 `TestShareResource` 整合測試（含離職移轉後接手人管理原分享連結驗證）。
- **搜尋引擎收錄／網站公開性設定（`ROBOTS_INDEXING_ENABLED`、動態 `/robots.txt` 與 `X-Robots-Tag`）**：
  - 新增 `RobotsFilter`（掛載於 `/*`）、`ConfigType.ROBOTS_INDEXING_ENABLED`（預設 `false`：不公開）、`POST /api/app/config_robots` 與「**設定 → 系統設定 → 搜尋引擎收錄（網站公開性）**」介面（含 16 國語系與檢視 `robots.txt` 按鈕）。
  - 不公開模式回傳 `Disallow: /` 並對所有回應加上 `X-Robots-Tag: noindex, nofollow, noarchive`；公開模式回傳 `Allow: /` 並一律排除 `/api/` 與 `/share.html`（`share.html` 另加上 `<meta name="robots" content="noindex, nofollow, noarchive">` 雙重保護分享連結）。

### 介面調整 (Changed)
- **系統設定語意一致化（`settings.config.html`、16 國語系）**：
  - 將「**重複檔案名稱上傳 (`file.duplicate`)**」與「**查看非自己所屬群組的成員**」兩項設定的標題、操作按鈕與狀態徽章由原本的「已啟用／未啟用」調整為語意更精準的「**允許／不允許**」，同步更新全部 16 國語系。
- **「文件搜尋（`#/document`）」與「標籤文件管理（`#/tagadmin`）」工作區重新定位**：
  - 將 `#/document` 更名為「**文件搜尋**」（放大鏡圖示 `fa-search`），移除新增文件按鈕、重複的資料夾導覽／匯出工具列與易生誤會的「檔案快速上傳（`#quick-upload-zone`）」區塊，並同步調整 16 國語系的 `onboarding.step3.description` 導覽文案。
  - 將 `#/tagadmin` 更名為「**標籤文件管理**」並開放所有非訪客使用者進入：左側頂部改為等寬等高並排的「**＋ 新增標籤**」（可收合，僅標籤管理者可見）與「**＋ 新增文件**」（同畫面 `#/tagadmin/document-add`、含匯入 EML、自動預選目前可寫入標籤，建立完成後自動展開左樹對應標籤或「未貼標籤的文件」並平滑捲動聚焦，右側直接進入「文件檢視與檔案」）；合併「全部展開／全部收合」為單一切換按鈕（同步套用於 `tag.html` 與 `tag.admin.html`）、統一第二排按鈕配色，並讓無標籤管理權限的一般使用者點選文件時智慧預設開啟「文件檢視與檔案」。
  - 系統內建 `plugins` 標籤預設改為隱藏（`PluginSeedService` `hidden = true`，同步更新 `TestPluginResource`），管理員可直接於「標籤文件管理」展開檢視並下載外掛 `.zip`。
- **搜尋命中關鍵字深淺色紅字粗體高亮（`document.html`、`main.less`）**：
  - 將文件列表全文搜尋命中摘要（`document.highlight`）中的關鍵字改為淺色模式深紅粗體（`#c62828`）、深色模式亮珊瑚紅粗體（`#ff6b6b`），提升辨識度與深淺主題對比度。
- **文件管理與標籤區文件列表精簡顯示（`#/document`、`#/tag`）**：
  - 移除文件管理左側清單（`document.html`）與標籤瀏覽區右側文件卡片（`tag.view.html`）中每筆文件下方的標籤徽章區塊，讓文件列表更簡潔易讀；文件詳細頁、資料夾導覽與進階搜尋的標籤篩選維持不變。

### 修復與安全性強化 (Fixed & Hardened)
- **加密模式修復 — 上傳同名檔案新版本撞檔失敗（`DirectoryUtil.java`、`FileUtil.java`、`StorageIntegrityService.java`、`TagZipExportService.java`、`FileResource.java`、`DocumentViewContent.js`）**：
  - 修正檔案加密模式（`file.encrypt = true`）下對既有檔案上傳同名新版本（如 `test.txt`）時，因平鋪寫入 `storage/test.txt` 觸發 `FileAlreadyExistsException` 導致「上傳失敗」的問題；新增 `DirectoryUtil.getCreateStorageDirectory()` 將加密模式新建檔案／新版本獨立存放於 `storage/{fileId}/`，同時完整向下相容既有的舊平鋪檔案，且完全不影響非加密模式邏輯。
  - 同步更新 `StorageIntegrityService` 與 `TagZipExportService` 唯讀路徑解析、於 `FileResource.add()` 正規化空字串 `previousFileId`、於 `DocumentViewContent.js` 補上 `uploadNewVersion` 失敗還原快照防呆，並新增 `TestFileResource#testEncryptedNewVersionSameFilename` 整合測試。
- **權限強化 — 貼附與移除標籤強制檢查 `WRITE` 權限（`DocumentResource`、`TagResource`、`SelectTag`、`TagAdminDocument`）**：
  - `GET /api/tag/list` 新增回傳各標籤之 `writable` 欄位（支援上層 ACL 繼承判斷）；於 `PUT /api/document`、`POST /api/document/{id}` 與 `POST /api/document/{id}/tags` 強制驗證：新增貼附或移除任何可見標籤皆須具備該標籤之 **`PermType.WRITE`** 權限（僅具 `READ` 唯讀權限者回傳 `403 Forbidden`，未更動的唯讀標籤與隱藏標籤則自動保留）。
  - 新增／編輯文件表單的 `select-tag`（`writable-only="true"`）僅列出可寫入標籤並鎖定既有唯讀標籤徽章（不影響文件搜尋的進階篩選）；「標籤文件管理」快速貼標樹保留階層顯示但鎖定唯讀標籤勾選框並顯示鎖頭，同步補齊 `TestTagResource` 整合測試。
- **重大修復 — 未加密模式下執行「重建索引＋補跑 OCR」誤刪原始檔案（`RebuildIndexAsyncListener.java`）**：
  - 修正當系統設定為未加密儲存模式（`file_encrypt = false`）時，執行「重建索引並補跑 OCR（`MISSING` / `ALL`）」因 `EncryptionUtil.decryptFile()` 直接回傳原始檔案路徑，導致 `finally` 區塊誤刪磁碟原始檔案的嚴重問題。
  - 比照 `PdfUtil.java` 加入雙重保護：僅於 `ConfigUtil.isFileEncrypt()` 為 `true` 時才產生暫存解密檔，且刪除前強制檢查加密模式與正規化絕對路徑不等於原始檔案路徑。
- **標籤管理區新增／刪除／移動檔案後即時刷新左樹檔案數（`DocumentViewContent.js`、`Tag.js`）**：
  - 修正於 `#/tagadmin/document/:id` 內上傳、刪除、移動檔案或變更版本時誤呼叫文件管理專屬 `$scope.viewDocument()` / `$scope.loadDocuments()` 的問題，改發送 `tagAdminDocumentFilesUpdated` 事件由 `Tag.js` 即時重刷左側已展開標籤與未貼標籤資料夾的檔案數 `(N)`（且不收合樹狀結構）。
- **修正文件檔案數統計誤計入歷史版本問題（`TagDao.java`、`DocumentDao.java`）**：
  - 於 `TagDao.findDirectDocumentsByTag()`、`TagDao.findUntaggedDocuments()` 與 `DocumentDao.getDocument()` 的 `file_count` 子查詢補上 `FIL_LATESTVERSION_B = true` 條件，避免非最新版的歷史檔案紀錄被重複計入，與文件列表統計完全一致。
- **修復標籤管理未儲存即提前更動左樹問題（`TagEdit.js`）**：
  - 移除載入標籤詳情時覆蓋 `$scope.tags` 陣列參考的行為，確保在右側修改上層標籤、名稱、顏色或說明時，於點擊「儲存」前不再提前變動左側標籤樹。
- **資安強化 — 本地 Tesseract 指令注入防禦（`CWE-78` / `CWE-88`，CodeQL #35）**：
  - 於 `TesseractOcrProvider` 新增嚴格白名單驗證 `validateTesseractCommand()`，在 `checkAvailability()` 與 `extractText()` 呼叫 `ProcessBuilder` 前強制檢查：僅允許空值（自動偵測）、硬編碼字面常數 `"tesseract"` / `"tesseract.exe"`，或正規化絕對路徑且檔名嚴格為 `tesseract` / `tesseract.exe` 之一般檔案。
- **可攜版 Demo 自動重啟防卡死與 Maven `clean` 檔案鎖清理**：
  - 更新 `scripts/templates/demo.bat` 與 `demo.sh`：改用 Jetty `--stop --stop-wait=30`、輪詢確認 Jetty 程序完全結束（逾時自動強制終止）、重試刪除 `data` 後再還原 `data.bak`、移除無人值守 `pause`，並支援啟動後中途補建 `data.bak` 於下個週期自動生效。
  - 更新 `scripts/templates/README.txt` 5 國語系說明（補齊 `[port] [hours]` 與 `RESET_SECONDS` 用法）。
  - 於 `pom.xml`（`pre-clean` 階段）與 `package-portable.sh` 加入跨平台（Windows / Linux / macOS）殘留 Jetty 自動停止防呆，解決 `mvn clean` 因 `start.jar` 被鎖住而失敗的問題。
- **GitHub Actions 抗 Maven Central HTTP 429 限流強化**：
  - 於 `.github/workflows/dast.yml`、`build-deploy.yml` 與 `codeql.yml` 設定 Google Maven Central 鏡像、降低併發（`-Daether.connector.basic.threads=2`）與 3 次退避重試（第 3 次自動回退原版 Maven Central）。

---

## [v3.01] - 2026-11-20

> **基準點**: `v2.09`（`v2` 分支）➜ `v3.01`（`master` 正式版）

### 新增功能 (Added)
- **標籤說明（`T_TAG.TAG_DESCRIPTION_C`，DB v47）**：
  - `T_TAG` 新增選填單行欄位 `TAG_DESCRIPTION_C VARCHAR(200)`（`dbupdate-047-0.sql`），於 `PUT /api/tag` 與 `POST /api/tag/{id}` 支援換行正規化、去除前後空白與空字串清空（`GET /api/tag/list`、`GET /api/tag/{id}` 與文件標籤欄位皆同步回傳）。
  - 更新全域 `$rootScope.getTagTooltip(tag)` 與 `.tooltip-inner`（`white-space: pre-line; text-align: left`），滑鼠移至全站任一標籤時兩行靠左顯示「建立者」與「說明」（無說明時僅顯示建立者）。
  - 於標籤管理新增區（`tag.admin.html`）與標籤編輯頁（`tag.edit.html`，含 `0 / 200` 字數提示）新增單行輸入框、於標籤瀏覽頁（`tag.view.html`）標題下方直接顯示說明，同步補齊 16 國語系與 `TestTagResource` 整合測試。
- **可攜版 Demo 定時重置腳本（`scripts/templates/demo.bat` 與 `demo.sh`）**：
  - 於 `start.bat` / `start.sh` 同目錄新增 `demo.bat [port] [hours]` 與 `demo.sh [port] [hours]`，支援每 24 小時自動優雅停機、刪除 `data`、從 `data.bak` 還原並重啟（若尚未建立 `data.bak`，則輸出英文提示並正常啟動不重置）。
- **本地 Tesseract OCR 檢查按鈕與 Windows 路徑防呆**：
  - 新增管理員端點 `POST /api/app/ocr/test-tesseract` 與「**設定 → 系統設定**」中的「**檢查 Tesseract**」按鈕，可即時驗證本機或自動偵測的 Tesseract 執行檔，回報版本、實際解析路徑與已安裝語言包清單。
  - 強化 `TesseractOcrProvider.resolveTesseractCommand()`：自動去除前後雙引號（相容 Windows 檔案總管「複製為路徑」），貼上資料夾路徑時自動補上 `tesseract.exe` / `tesseract`。
- **執行期軟體組成清單（SBOM，限管理員存取）**：
  - 新增獨立「**設定 → 軟體組成 (SBOM)**」（`#/settings/sbom`）頁籤與管理員專屬端點（`GET /api/app/sbom[?refresh=true]`、`GET /api/app/sbom/export?format=cyclonedx|csv`），首次自動掃描並持久化存檔（`sbom-cache.json` 含快照產生時間），後續檢視與匯出皆直接讀取快取，僅於按下「重新整理」時才重新掃描。
  - 全程於行程內動態盤點（不呼叫外部 CLI 子行程）：執行環境與伺服器（系統版本、OS、Java/JVM、Servlet 容器 / Jetty、資料庫引擎與 JDBC Driver、OCR 引擎設定）、已安裝外掛（`PluginDao`）、實際載入的後端 Java 套件（`WEB-INF/lib/*.jar`）與建置期掃描的前端函式庫（`dist/sbom-frontend.json`）。
  - 提供可展開／收合面板、關鍵字即時篩選、標準 **CycloneDX 1.5 JSON**（含 Maven `purl`）與 UTF-8 BOM **CSV** 匯出；同步自公開頁尾與 `GET /api/app` 移除 Jetty／Java 版本資訊以強化資安。
- **群組描述（`T_GROUP.GRP_DESCRIPTION_C`，DB v46）**：
  - `T_GROUP` 新增選填欄位 `GRP_DESCRIPTION_C VARCHAR(200)`（`dbupdate-046-0.sql`），新增與編輯群組（含內建 `administrators`、`tag_editor` 群組）皆支援設定描述。
  - 於群組管理（列表、編輯表單、上層群組下拉）、使用者與群組頁、文件／標籤／簽核 ACL 選擇器與權限徽章（`<acl>` 含 tooltip）及線上考試外掛權限彈窗全面顯示（並為 `ExamTeachers` 與 `ExamStudents` 提供預設英文描述），搜尋維持只比對群組名稱。
- **監控頁索引重建即時進度與補跑 OCR**：
  - 新增執行緒安全的後端進度追蹤器 `ReindexProgress` 與 `GET /api/app/batch/reindex/status`，管理者切換分頁再回到「監控」依然可即時檢視進度條、目前處理項目與 OCR 失敗檔案原因明細。
  - `POST /api/app/batch/reindex` 新增 `ocr_mode`（`NONE` / `MISSING` / `ALL`），可針對空白文字檔案批次補跑 OCR（自動略過 `.zip` 等不支援格式）。
- **Dockerfile 自動抓取最新 Jetty 12 穩定版**：
  - 將 `Dockerfile` 改為 `ARG JETTY_VERSION=latest`，建置時自動由 Maven Central 取得最新 Jetty 12 穩定版（並支援 `--build-arg JETTY_VERSION=...` 鎖定特定版本）。
- **外掛平台底座 (`plugin-platform`、DB v44–45)**：
  - 新增 `T_PLUGIN`、`T_PLUGIN_TAG`、`T_PLUGIN_DATA` 資料表（`dbupdate-044-0.sql`）與 `T_TAG.TAG_HIDDEN_B` 隱藏標籤欄位（`dbupdate-045-0.sql`），全面相容 H2 2.3 與 PostgreSQL。
  - 支援 `.zip` 外掛上傳安裝（`POST /api/plugin/upload`）、`plugin.json` 解析（`id`、`version`、`entry`、`nav_label_i18n`、`default_locale`、`handler`、`protected_tags`）、Zip Slip 路徑遍歷防呆、免重啟啟用/停用/解除安裝、`PluginHandler.onEnabled(installerUserId)` 首次啟用資源建置掛鉤、頂部導覽列動態選單、`#/settings/plugins` 管理頁與 `#/plugin/:id` 沙箱 `iframe` 容器。
  - 新增**配合加密鎖定之內建外掛自動植入機制（`PluginSeedService`、`ConfigType.PLUGINS_SEEDED`）**：將 `docs-plugins/dist/plugin-*.zip` 打包入 `docs-core`（`classpath:plugins/`）；全新系統等待管理員確認檔案加密設定（`POST /api/app/file_encrypt`，含前端轉圈處理中提示與 `$state.reload()` 自動重新整理列表）後，立即建立僅 `admin` 可見的 `plugins` 標籤（`hidden = false`）與 `plugins` 文件（含 16 語系 HTML `<ul><li>` 條列說明與 `.zip` 附件），重啟或升級時自動冪等比對補入。
  - 新增通用 JSON 儲存端點（`/api/plugin/{id}/data/{collection}/{key}`，對非管理員自動遞迴遮罩 `_`、`secret_`、`correct_` 機密欄位）與後端擴充 `PluginHandler` SPI（`/api/plugin/{id}/action/{action}`）。
  - 新增**隱藏標籤 (`hidden` 與母子連帶隱藏 `effective_hidden`)** 屬性：隱藏標籤及其子孫標籤於一般文件側邊欄與標籤選擇器隱藏，僅於「標籤管理 (`#/tagadmin`)」顯示供設定權限。
  - 雙層標籤防誤刪與防改名機制：外掛安裝期間（啟用或停用）禁止刪除、更名或變更固定保護標籤可見性（`ProtectedPluginTag`）；透過 `PluginHandler.findTagReferenceReason` 通用檢查，凡被外掛資料引用的標籤亦禁止刪除（`TagInUseByPlugin`）。
  - 建立**外掛多語系標準規範**（`locale/<lang>.json`、`nav_label_i18n` 與 `$rootScope.$on('$translateChangeSuccess')` 即時刷新外掛語系）及外掛靜態資源嚴格 CSP / `X-Content-Type-Options: nosniff` 安全標頭。
  - 新增含 Mermaid 架構與序列圖之 AI 外掛開發鐵律與指南：`docs-plugins/AGENTS.md`、`docs-plugins/PLUGIN_GUIDE.md`、`docs-plugins/PLUGIN_GUIDE.zh-TW.md`。
- **線上考試示範外掛 (`online-exam` v1.3.1、`docs-plugins/dist/plugin-online-exam.zip`)**：
  - 實作 `ExamPluginHandler`、`GiftParser` 與 `ExamPdfExporter`（採用內建 `Noto Sans CJK TC` 字型）。
  - **首次啟用自動建置展示環境 (`onEnabled`)**：首次啟用外掛時自動建立無特權群組（`ExamTeachers`、`ExamStudents` 含英文描述）、英文階層標籤（`OnlineExamMaterials` -> `SecurityFundamentals`）、內含**全部 16 國語系專屬檔名與內容之 PDF 講義**的教材文件 `Security Fundamentals Guide`（支援點擊「眼睛」檢視純文字與 Lucene 全文檢索），以及綁定該教材之 16 語系考卷 `SEC-001`（`Security Fundamentals Assessment`）。
  - **分頁、排序與搜尋之多份參考文件綁定（嚴格權限模式）**：出題老師可透過分頁（單頁上限 100 筆）、名稱／建立日期排序（預設最新優先）與搜尋勾選具備 `READ` 權限的參考文件（自動排除系統 `plugins` 與隱藏標籤文件）；考生僅會看到具備 `PermType.READ` 權限的參考文件連結。
  - **全三頁籤統一搜尋、篩選、排序與分頁**：「我的考卷」、「成績紀錄」與「出題與考卷管理」皆提供搜尋、狀態篩選、欄位排序與每頁筆數切換（`10 / 20 / 50 / 100`）；建立考卷表單預設收合，並將「解析預覽」統一更名為「語法檢查」（16 語系同步）。
  - **三層權限架構**：最高管理員（`ADMIN` 或具備 `Online Exam Admin` 標籤）> 各科出題老師（由最高管理員指派啟用中帳號/群組，如 `ExamTeachers`，跨科別嚴格隔離）> 應試學生（依考卷勾選之啟用中帳號/群組授權）。
  - **Moodle GIFT 匯入與 100 分均分批改**：支援單選（`=`/`~`）、多選（`~%...%`）、是非題（`{TRUE}`/`{FALSE}`），內建 512 KB / 500 題 DoS 防護、考卷代號/版號、停用/啟用切換、刪除考卷前已應試人數警告且刪除後保留歷史成績快照，以及後端批改不洩漏正解與漏答送出提醒。
  - **互動可填寫空白考卷 PDF 與 16 語系 PDF 匯出**：空白考卷 PDF 採用標準 **AcroForm** 表單（可填寫姓名／日期文字欄、單選／是非圓鈕群組、多選核取方塊，並簡化表頭移除「空白考卷」字樣）；已作答考卷 PDF 維持唯讀與考試時間斜角半透明浮水印（無正解）；橫向成績總覽彙總表 PDF 支援 16 語系動態輸出。
  - **完整 16 國語系與專屬樣板手冊**：提供系統全部 16 國慣用語翻譯與完整架構拆解手冊（`docs-plugins/online-exam/README.md` / `README.zh-TW.md`）。

### 修復 (Fixed)
- **可攜版打包腳本優化（`scripts/package-portable.ps1` 與 `package-portable.sh`）**：不再寫死 `jetty-home-12.1.14.zip`，改為自動以版本號排序選取 `tools/jetty/` 目錄下最新版本的 `jetty-home-*.zip`；若該目錄為空，則自動連線至 `https://jetty.org/download.html`（含 Maven Central 備援）抓取並下載最新版 Jetty 12。
- **Maven 版本號與頁尾顯示修正 (`v3.01`)**：將 `pom.xml`、`docs-core/pom.xml`、`docs-web-common/pom.xml` 與 `docs-web/pom.xml` 的 `<version>` 統一更新為 `3.01`，使頁尾正確顯示 `v3.01`。
- **16 國語系翻譯完整性修復**：修正 `zh_CN.json`（`download`、`rename`、`yes`、`tag.created_by`、`document.edit.allow_download_help`）與 `zh_TW.json`（`yes`、`group_used_*`、`base_dn`）殘留英文之項目，補齊全部 16 國語系的外掛管理與隱藏標籤翻譯，並將 `document.view.content.html` 檔案清單模式表頭（`Filename`、`Type`、`Size`、`Version`）改為多語系變數。
- **頁尾巴西葡萄牙語 (`pt_BR`) 顯示修復**：於 `index.html` 語系選單補上 `<span ng-switch-when="pt_BR">Português (Brasil)</span>`，解決切換後只剩倒三角形、語言名稱消失的問題。

---

## [v2.08] - 2026-11-17

> **基準點**: `v2.07` (`34b41713`) ➜ `v2.08`

### 新增功能 (Added)
- **單一標籤專注檢視模式（`#/tag/:id?focus`）**：
  - 於 `tag.html` 左側標籤樹工具列「匯出報表」左邊新增 **「專注此標籤／顯示全部」** 切換按鈕與 **「於新分頁專注開啟」** 連結，支援透過 `?focus=<tagId>` 在專注子樹內自由瀏覽子標籤、文件與預覽檔案。
- **可擴充 AI 視覺辨識 OCR 與 PDF 逐頁智慧偵測（`OcrService`、`OllamaOcrProvider`、`OpenAiCompatibleOcrProvider`、`TesseractOcrProvider`）**：
  - 將 `FileUtil.ocrFile` 重構為策略模式（`OcrProvider`／`OcrService`），未來新增其他 AI 或雲端辨識服務時無須修改文件與圖片處理核心。
  - 內建支援 **Ollama Vision**（`/api/chat` 與 `/api/tags`）與 **OpenAI 相容 Vision API**（`/v1/chat/completions` 與 `/v1/models`，支援 Bearer API Key，可對接 vLLM、LM Studio、llama.cpp、OpenAI、Gemini 等），送圖長邊自動縮放至 ≤1536px 以降低 VRAM／記憶體消耗並預防 OOM。
  - 強化 `TesseractOcrProvider`，於 Windows 環境未將 `tesseract` 加入 `PATH` 時自動偵測預設安裝路徑（Linux／Docker 維持直接走系統 `PATH`），並支援自訂執行檔路徑。
  - 升級 `PdfFormatHandler` 為**逐頁文字品質偵測**（自動剔除空白頁、純頁碼雜訊與 `\uFFFD` 亂碼頁），乾淨的內嵌文字頁直接沿用、無效或純圖頁自動送交 OCR；另提供「強制所有 PDF 頁面使用 AI OCR」開關，並在 AI OCR 失敗（如 Ollama OOM）時依序自動降級為 Tesseract 與 PDF 內嵌文字提取雙重保底。
  - 修復在「新增文件」尚未存檔前上傳檔案（`language == null`）時 `FileResource.add`／`PdfFormatHandler`／`ImageFormatHandler` 跳過 OCR 的問題，改以 `ConfigType.DEFAULT_LANGUAGE` 補位。
  - 於「一般設定」新增 OCR 引擎切換、測試連線／讀取模型清單（`POST /api/app/ocr/config`、`POST /api/app/ocr/test`）與完整 16 國語系翻譯。
- **巴西葡萄牙語（`pt_BR`）介面語系** — 參照上游議題 [`sismics/docs#779`](https://github.com/sismics/docs/issues/779)：
  - 新增符合巴西葡萄牙語語法與慣用詞彙的 `pt_BR.json` 及地區格式 `angular-locale_pt_BR.js`，於 `docs/app.js`、`share/app.js` 與 `LocaleUtil.java` 註冊，並於語系切換選單加入 `Português (Brasil)`。
- **停用帳號搜尋權限過濾與管理者刪除線顯示**：
  - 更新 `UserCriteria`／`UserDao.findByCriteria`、`UserResource.list` 與 `AclResource.targetList`，非管理員無法搜尋或列出已停用帳號；管理員仍可檢索並於 `usergroup.html`、`Acl.js`、`settings.group.edit.html` 與 `document.html` 以刪除線樣式區別標示。
- **FormosaBearV 台灣黑熊吉祥物 Logo 改版與設計理念（`logo.png`、`title.png`、`icons/*`）**：
  - **設計理念**：為擺脫市面上常見扁平大頭黑熊圖示容易撞臉的問題，重新將品牌核心「台灣意象 × 知識典藏與檢索」融入視覺敘事——描繪一隻戴著金色圓框老學究眼鏡的**台灣黑熊**側影（保留胸前標誌性白色 V 字），正踏上木梯從書架高處取下一本亮橘黃色書籍。同時考量 32×32～64×64 導覽列與 PWA 小圖示的可讀性，刻意將構圖極簡化為「單條書架橫線、單本亮色書、兩階粗木梯與放大比例的黑熊主體」，兼顧故事性與小尺寸辨識度。
- **頁尾版本號與專案連結優化**：
  - 移除 `index.html` 與 `share.html` 頁尾 `API 說明文件` 連結，並將 `v{{ app.current_version }}` 設為連往 `https://github.com/kafeiou/FormosaBearV-Docs` 的外部連結。
- **垃圾桶功能（`#/trash` 與 `/api/trash`）**：
  - 將系統軟刪除暫存機制（`ConfigType.FILE_DELETE` / `delete/storage/`）與全新「垃圾桶」管理介面（`#/trash`）及 REST API（`TrashResource`）完整打通。
  - 支援分頁檢視「已刪除的文件」與「已刪除的檔案」（單獨被刪除的附件或歷史版本），提供一鍵還原、單筆永久刪除與「清空垃圾桶」（`DELETE /api/trash/empty`），並支援管理員切換檢視全系統所有使用者的垃圾桶項目。
  - 內建還原防呆機制：檢查儲存空間配額（`QuotaReached`）、重複檔名限制（`FileNameDuplicate`）、母文件是否仍在垃圾桶中（`ParentDocumentInTrash`），以及自動重整檔案版本序列。
  - 新增 `AuditLogType.RESTORE` 稽核類型，還原文件或檔案時於最新動態顯示「文件 還原」／「檔案 還原」。
  - 調整頂部導覽列順序為：**「標籤」 -> 「標籤管理」 -> 「文件管理」 -> 「垃圾桶」**。
- **`.txt`、`.md`、`.html` 原生預覽與防偽浮水印 + 巨型檔案記憶體保護機制**：
  - 新增視窗內分段串流預覽 API（`GET /api/file/{id}/text`），支援 Markdown（`.md` 排版渲染）、HTML（`.html` 透過無腳本 `sandbox=""` 安全沙盒渲染，阻絕 XSS）與純文字（`.txt` / `.csv`）視窗內直接預覽、超大檔案分頁翻閱、排版／原始碼／擷取文字視圖切換、依下載權限連動文字選取複製限制，以及全程覆蓋防偽浮水印。
  - 更新 `TextPlainFormatHandler` 採用內建 `Noto Sans CJK TC` 中文字型與 4 KB 邊界讀取產生第 1 頁縮圖，並透過 `Jsoup` 擷取 HTML 純文字供全文檢索。
  - 更新 `PdfFormatHandler` 與 `PdfUtil` 全面採用 `MemoryUsageSetting.setupMixed(50MB)` 磁碟暫存緩衝，確保上千頁巨型 PDF 預覽與處理不會耗盡 JVM 記憶體。
- **群組可見範圍控管與自我權限移除防呆（`GroupResource`, `AclResource`, `ConfigType.GROUP_VIEW_ALL`）**：
  - 新增自我鎖定防呆：禁止使用者於群組移除自己（`CannotRemoveSelfFromGroup`）、禁止更名或刪除內建系統群組 `administrators` 與 `tag_editor`（`ProtectedGroupName`），並禁止非系統管理員在標籤／文件 ACL 中誤刪自己帳號或自己所屬群組的權限（`CannotRemoveOwnAcl`）。
  - 限制 `GET /api/group` 與 `GET /api/group/{name}`：永遠禁止 `guest` 訪客查看群組與成員，一般使用者預設僅能查看自己所屬群組與成員。
  - 於「設定 ➜ 一般設定」新增全域開關（`POST /api/app/group_view_all`），管理員可依需求開啟「允許查看非自己所屬群組的成員」。

### 安全性修正 (Security)
- **修復 `tag_editor` 群組誤授 `ADMIN` 權限之提權漏洞（`dbupdate-043-0.sql`）**：
  - 移除 `tag_editor` 群組原本錯誤綁定的 `GRP_IDROLE_C = 'admin'` 角色，確保僅加入 `tag_editor` 的一般帳號不會再繼承 `BaseFunction.ADMIN` 最高系統管理員權限（可編輯 `administrators` 或修改系統設定），同時保留其原有的「標籤管理」與「檔案靜默抽換」權限，並於 `GroupResource` 明確保護 `tag_editor` 不被刪除。

### 問題修正 (Fixed)
- **修復暫存檔清理 `NoSuchFileException` 例外（`FileService.deleteTemporaryFiles`）**：
  - 改用 `Files.deleteIfExists` 並將 `referenceSet.remove(ref)` 置於 `finally` 區塊，避免 OCR 或檔案處理器提早刪除暫存檔時造成背景排程噴錯。

---

## [v2.07] - 2026-11-15

> **基準點**: `v2.06` (`f52d698f`) ➜ `v2.07`

### 新增功能 (Added)
- **使用者顯示名稱（`name`）與 LDAP 姓名屬性整合（`dbupdate-042-0.sql`）**：
  - `T_USER` 新增選填顯示名稱欄位（`USE_NAME_C`，最長 100 字元），支援管理員於「設定 ➜ 使用者」及所有登入使用者（含 LDAP 帳號）於「設定 ➜ 個人帳號」自行維護。
  - `T_LDAP_CONFIG` 新增可自訂之預設姓名屬性（`LDC_DEFAULT_NAMEATTR_C`，預設 `cn`），LDAP 登入時依序讀取 `設定屬性 -> displayName -> cn`，並僅於首次建帳或目前 `name` 為空時填入。
  - 導覽列、文件／標籤建立者、協編者、留言、已授權 ACL／群組、Audit Log 顯示乾淨的 `name` 並搭配 `uib-tooltip` 即時氣泡顯示帳號代號；標籤與文件建立者氣泡、人員挑選選單（支援以姓名或代號雙向搜尋）與預覽浮水印則顯示 `name (username)`。
  - 於「設定 ➜ 個人帳號」新增文件建立時間格式自訂選項（預設 `yyyy-MM-dd HH:mm`、可切換 `yyyy-MM-dd` 或相對時間）。
  - 補齊並完成全部 15 種支援語系之翻譯。

### 安全性修正 (Security)
- **修復分享 ID 偽造漏洞 (`CVE-2026-50885` / `CWE-284`)** — 參照上游議題 [`sismics/docs#792`](https://github.com/sismics/docs/issues/792)（由 `@vmario89` 回報）：
  - 更新 `BaseResource.getTargetIdList(String shareId)`，在將 `shareId` 加入 ACL 目標清單前先透過 `ShareDao.getActiveById(shareId)` 驗證分享是否存在且有效，防止偽造 `share` 參數繞過文件與檔案讀取權限檢查。
  - 新增回歸測試 `TestShareResource.testShareSpoofingBlocked`。
- **強制檔案更新與刪除需具備 `WRITE` 權限 (`CVE-2025-11853` / `CWE-266`, `CWE-284`)** — 參照上游議題 [`sismics/docs#792`](https://github.com/sismics/docs/issues/792)（由 `@vmario89` 回報）：
  - 更新 `FileResource.delete`（`DELETE /api/file/{id}`），刪除關聯至文件之檔案時強制檢查所屬文件的 `PermType.WRITE` 權限。
  - 更新 `FileResource.update`（`POST /api/file/{id}`），將檔案附加或移動至其他 `document_id` 時強制檢查目標文件的 `PermType.WRITE` 權限。
  - 新增回歸測試 `TestFileResource.testFileWritePermissionEnforced`。
- **安全政策與自動化靜態／動態掃描**：
  - 新增 `SECURITY.md`，說明支援版本（`2.07+`）與 GitHub 私人漏洞回報管道。
  - 新增 `.github/workflows/codeql.yml`，啟用 GitHub CodeQL（`java-kotlin` 與 `javascript-typescript`）靜態安全掃描。
  - 新增 `.github/workflows/dast.yml`，排程／手動對 Jetty 實例執行 OWASP ZAP Baseline DAST 動態基準掃描。

### 問題修正 (Fixed)
- **標籤管理父標籤下拉選單自動收合與過濾修正（`Tag.js`、`tag.admin.html`、`TagEdit.js`、`tag.edit.html`）**：
  - 改用物件屬性（`parentSelector.open`、`parentSelector.search` 與 `editParentSelector`）修正 AngularJS `ng-if` 子作用域遮蔽問題，點選第二層等父標籤後選單會自動收合，不再遮擋下方的「新增」按鈕；同時修正 `| filter:` 多餘參數衝突以維持樹狀過濾正常運作。

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
- **新增塞爾維亞語（`sr`）完整語系與 OCR 支援**（上游 Issue [sismics/docs#790](https://github.com/sismics/docs/issues/790)，感謝 [@MilosQL](https://github.com/MilosQL) 貢獻）：
  - 匯入塞爾維亞語系檔（[sr.json](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/sr.json)、[angular-locale_sr.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/angular-locale_sr.js)）並補齊 FormosaBearV 全部翻譯鍵值（與 `en.json` 100% 對齊），同時於 [angular.timeago.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/lib/angular.timeago.js) 新增 `sr` 相對時間字典。
  - 於 [app.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/app.js)、[share/app.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/share/app.js)、[index.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/index.html) 與 [share.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/share.html) 註冊 `sr` 語系選單，並於 [Constants.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/constant/Constants.java)、[LocaleUtil.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/util/LocaleUtil.java) 與 [Dockerfile](file:///W:/developer/project/github/FormosaBearV-Docs/Dockerfile) 補上 `srp` / `srp_latn`（`tesseract-ocr-srp`、`tesseract-ocr-srp-latn`）OCR 支援。

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
  - 將預設登入頁面背景圖片（[background.jpg](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/resources/image/background.jpg)）替換為台灣玉山北峰實景風景照（作品：*Deep blue sky over Yushan North Peak.jpg*，攝影：Moran Tsai；出處：[Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Deep_blue_sky_over_Yushan_North_Peak.jpg)／[Flickr](https://www.flickr.com/photos/moran1029/48890432256/)，授權：[CC0 1.0 通用公眾領域貢獻宣告](https://creativecommons.org/publicdomain/zero/1.0/)）。
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
