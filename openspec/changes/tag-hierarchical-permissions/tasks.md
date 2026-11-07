# Tasks: 階層式標籤權限與中斷繼承實作清單

## 1. 資料庫遷移與底層實體支援 (Database & Entity Support)

- [x] 1.1 建立 DB Migration v35 腳本（`dbupdate-035-0.sql`），於 `T_TAG` 新增 `TAG_INHERIT_ACL_B BOOLEAN DEFAULT TRUE NOT NULL` 欄位，並驗證 SQL 語法正確性
- [x] 1.2 提升三處 `config.properties` 之 `db.version=35`，並於 `Tag.java` 與 `TagDto.java` 增加 `inheritAcl` 屬性與 getter/setter
- [x] 1.3 於 `TagDao.java` 之建立（`create`）、更新（`update`）與查詢（`findByCriteria`）邏輯中加入 `TAG_INHERIT_ACL_B` 欄位映射，並透過單元測試驗證持久化與預設值

## 2. 核心權限回溯與多標籤聯集引擎 (Core Hierarchy ACL & Union Resolution Engine)

- [x] 2.1 於 `TagDao.java` 實作 `getEffectiveAclTagIds(String tagId)` 向上回溯演算法，內建 `visited` 環狀引用防禦，並驗證遇到 `inheritAcl == false` 時立即阻絕向上收集
- [x] 2.2 更新 `AclDao.checkPermission`，將標籤檢查擴展為包含所有有效祖先標籤，驗證子標籤自動繼承 READ 與 WRITE、權限疊加與中斷繼承阻隔效果
- [x] 2.3 更新 `LuceneIndexingHandler.java` 與文件查詢條件，確保文件清單篩選與全文搜尋支援多標籤祖先權限聯集
- [x] 2.4 實作路徑容器標籤支援：當使用者對某子標籤具備權限但對父標籤無權限時，將父標籤標示為 `is_container: true` 回傳，以供維護樹狀導航架構
- [x] 2.5 於 `TestTagResource.java` 擴充權限單元測試案例（涵蓋：子標籤自動繼承 READ/WRITE、多層權限疊加、中斷繼承阻斷非授權人員、多標籤聯集讀取、路徑容器標記），確保測試 100% 通過

## 3. REST API 與繼承狀態轉換邏輯 (REST API & State Transition Helpers)

- [x] 3.1 於 `TagResource.java` 的 `@PUT /tag` 與 `@POST /tag/{id}` 端點支援 `inherit_acl` 參數傳遞，並於 `@GET /tag/{id}` 與 `@GET /tag/list` 回傳該狀態與 `is_container` 標記
- [x] 3.2 於 `TagResource.java` 實作狀態轉換邏輯：中斷繼承（`inherit_acl` 轉 false）時自動將父標籤有效 ACL 複製為本標籤直接 ACL；恢復繼承（`inherit_acl` 轉 true）時自動清空本標籤直接 ACL，並撰寫 API 驗證
- [x] 3.3 於 `TagResource.java` 提供取得標籤「繼承自父層之 ACL 清單」端點或附隨資訊，供前端標籤編輯對話框展示繼承來源

## 4. 前端 UI 互動與視覺標記 (Frontend UI & Visual Badges)

- [x] 4.1 於 `tag.edit.html` 權限設定區塊新增「繼承上層標籤權限」開關，並於 `Tag.js` 實作重新勾選時「確認清空自訂權限」之防呆確認提示
- [x] 4.2 於 `tag.edit.html` 清晰分列「繼承自父層之權限」（附帶來源標籤標籤徽章、無法直接刪除）與「本標籤專屬權限」（可新增/刪除）
- [x] 4.3 於 `tag.admin.html` 與 `tag.html` 樹狀節點中，針對中斷繼承的標籤名稱旁顯示鎖頭圖示（`fa-lock text-warning`）與 Tooltip 提示；針對 `is_container` 父標籤顯示為灰色不可點擊
- [x] 4.4 於多國語系檔（`zh_TW.json`, `zh_CN.json`, `en.json`）新增相關中英文字串（如繼承開關標籤、恢復繼承清空確認、繼承來源徽章、隔離鎖頭提示）
- [x] 4.5 執行 Maven 前端編譯與 WAR 打包（`mvn package -DskipTests`），驗證端到端部署運行正常

## Workflow follow-up

- 完成實作並進行端到端測試驗證。
- 遵循 OpenSpec 工作流歸檔變更並自動同步 `openspec/specs/README.md`。
