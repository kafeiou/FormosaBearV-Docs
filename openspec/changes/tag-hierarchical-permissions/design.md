# Technical Design: 階層式標籤權限與中斷繼承

## Context

參見 `proposal.md - Why` 與 `specs/tag-permissions/spec.md`。

在目前 FormosaBearV-docs 架構中：
* 權限實體 `T_ACL` 透過 `ACL_SOURCEID_C` 指向文件 ID 或標籤 ID，透過 `ACL_TARGETID_C` 指向使用者、群組或共享 ID。
* `AclDao.checkPermission` 與 `LuceneIndexingHandler` 在檢查文件存取權限時，僅檢查「直接設定在文件的 ACL」以及「直接掛載在文件的標籤 ACL（`dta.DOT_IDTAG_C = a.ACL_SOURCEID_C`）」。
* 標籤實體 `T_TAG` 具備 `TAG_IDPARENT_C` 支援多層階層，但權限檢查尚未向上遞迴查詢祖先標籤。

## Goals / Non-Goals

**Goals:**
* 實現標籤向上回溯祖先權限繼承（READ 與 WRITE 均完整繼承）。
* 實現中斷繼承開關（`TAG_INHERIT_ACL_B`）：設為 `false` 時切斷向上回溯鏈，成為獨立安全邊界。
* 實現中斷繼承自動複製父層 ACL，以及恢復繼承自動清除自訂 ACL 的流暢轉換。
* 實現樹狀導航中未授權父標籤的「路徑容器」灰色節點展示。
* 標籤編輯對話框清晰分列「繼承自父標籤之權限」與「本層專屬權限」。
* 確保升級、資料庫遷移（v35）以及備份還原之 100% 向下相容。

**Non-Goals:**
* 不調整文件自身直接掛載之 ACL 機制（文件自身的 ACL 依舊獨立有效）。
* 不變更既有使用者、群組與共享（User/Group/Share）之安全性底層結構。

## Decisions

### 決策 1：權限祖先鏈解析策略採「動態回溯解析（Dynamic Ancestor Resolution）」
* **架構選擇**：在後端 `TagDao` 提供標籤祖先鏈走訪方法 `getEffectiveAclTagIds(tagId)`：
  ```java
  public List<String> getEffectiveAclTagIds(String tagId) {
      List<String> effectiveIds = new ArrayList<>();
      Set<String> visited = new HashSet<>();
      String currentId = tagId;
      while (currentId != null && visited.add(currentId)) {
          effectiveIds.add(currentId);
          Tag tag = getById(currentId);
          if (tag == null || !tag.isInheritAcl()) {
              break; // 遇到中斷繼承，終止向上回溯
          }
          currentId = tag.getParentId();
      }
      return effectiveIds;
  }
  ```
* **理由**：
  * 組織架構中的標籤樹層數有限（深度 $\le 10$），單次走訪只需數毫秒以內。
  * 相比於實體複製派發（Propagation），動態回溯在「修改父標籤權限」或「搬移子標籤」時**零資料庫批次寫入**，即時生效且零幽靈資料風險。

### 決策 2：中斷繼承與恢復繼承之狀態轉換邏輯
* **中斷繼承（`inherit_acl` 由 `true` 改為 `false`）**：
  * 系統自動將其父標籤目前擁有的所有有效 ACL（READ 與 WRITE）複製為本標籤的直接 ACL。
  * 管理者無需從空白開始重新配置，可直接在既有權限列表上刪除或微調，操作步驟最少。
* **恢復繼承（`inherit_acl` 由 `false` 改為 `true`）**：
  * 系統自動刪除該標籤的所有自訂 ACL，使該標籤重新完全由上層祖先權限所掌控，避免過期自訂權限殘留造成權限污染。

### 決策 3：未授權父標籤之路徑容器（Container）展示策略
* **前端與 API 協同**：
  * 當使用者對某標籤具備存取權限，但對其父標籤無權限時，API 仍回傳父標籤之基本中繼資料（`id`, `name`, `parent`），並附加標記 `is_container: true`。
  * 前端標籤樹（`tag.admin.html`, `tag.html`）接收到 `is_container` 標籤時：
    * 顯示名稱以維持階層架構。
    * 樣式呈現為灰色淡化（`text-muted`）且取消點擊連結，不顯示其內部文件，防止越權檢視。

### 決策 4：標籤編輯 UI 之繼承權限展示分流
* **介面配置**：
  * 於 `tag.edit.html` 權限區塊增加 `[v] 繼承上層標籤的權限` 開關。
  * 若標籤開啟繼承，介面分兩區塊呈現：
    1. **繼承自父層之權限**：列出使用者/群組名稱與權限等級，並附帶 `(繼承自: 部門資料)` 徽章，不顯示刪除按鈕。
    2. **本標籤專屬權限**：列出本層直接設定之權限，提供刪除與新增功能。
  * 於標籤樹中，若標籤 `inherit_acl === false`，於名稱旁顯示金色鎖頭圖示（`fa-lock text-warning`）。

### 決策 5：資料庫綱要與版本升級
* **DB Migration v35 (`dbupdate-035-0.sql`)**：
  ```sql
  ALTER TABLE T_TAG ADD COLUMN TAG_INHERIT_ACL_B BOOLEAN DEFAULT TRUE NOT NULL;
  ```
* **版本號推進**：
  * `docs-core/src/main/resources/config.properties`、`docs-web/src/dev/resources/config.properties`、`docs-web/src/prod/resources/config.properties` 之 `db.version` 提升為 `35`。

## Risks / Trade-offs

* **[Risk] 標籤環狀引用（Cyclic Parent Reference）導致無窮遞迴**
  $\to$ **[Mitigation]** 走訪演算法內建 `Set<String> visited` 環狀防禦偵測，一旦偵測到重複 ID 即刻中斷走訪；並在 `TagResource` 的修改與新增驗證中禁止設定自身或子孫為父標籤。
* **[Risk] 搜尋引擎（Lucene）與直接 SQL 權限檢查的一致性**
  $\to$ **[Mitigation]** 在 `LuceneIndexingHandler.java` 與 `AclDao.checkPermission` 中，將文件所貼附的各標籤透過 `getEffectiveAclTagIds` 展開為有效標籤清單後再行比對，確保搜尋結果與單一文件讀取判定完全一致。
* **[Risk] 恢復繼承時意外清空管理者需要的特殊權限**
  $\to$ **[Mitigation]** 在前端點選重新勾選「繼承上層標籤權限」時，跳出確認對話框：「重新啟用繼承將會清除本層目前的自訂權限，由上層完全接管，是否確定？」，經管理者確認後才送出。

## Migration Plan

1. 建立 `docs-core/src/main/resources/db/update/dbupdate-035-0.sql`。
2. 升級 `config.properties` 至 `db.version=35`。
3. 部署啟動時，Hibernate / DbOpenHelper 自動執行 v35 升級腳本。
4. 既有標籤資料庫欄位自動預設填入 `true`，既有系統無縫繼續運作。
