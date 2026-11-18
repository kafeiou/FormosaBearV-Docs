# FormosaBearV Docs 外掛開發指南

本指南說明如何為 **FormosaBearV Docs**（v3.0.0 以上）開發、多語系化、打包與安全部署第三方外掛套件。

---

## 1. 架構總覽

FormosaBearV Docs 提供兩種外掛整合模式：

1. **純前端外掛 + 通用 JSON 資料儲存 (`T_PLUGIN_DATA`)**：
   - 上傳包含 `plugin.json`、靜態 HTML/CSS/JS 與 `locale/*.json` 的 `.zip` 壓縮檔。
   - 宿主透過獨立路由 `#/plugin/<id>` 的 `iframe` 容器載入畫面。
   - 直接呼叫 `/api/plugin/{id}/data/{collection}/{key}` 讀寫資料，並內建非管理員機密欄位自動遮罩。
2. **全端擴充外掛 (`PluginHandler` SPI)**：
   - 前端 `.zip` 套件搭配註冊於 `PluginService` 的 Java `PluginHandler`。
   - 透過 `/api/plugin/{id}/action/{action}` 執行自訂商業邏輯（如後端批改、權限隔離、PDF 二進位串流匯出）。

---

## 2. 套件目錄結構與打包規範

```text
my-plugin/
├── plugin.json          # 必須位於 ZIP 根目錄
├── locale/              # 標準多語系 JSON 目錄
│   ├── en.json          # 必備預設 / Fallback 語系
│   └── zh_TW.json       # 對應主系統語系代碼的翻譯檔
└── ui/
    ├── index.html       # 對應 plugin.json 的 "entry" 路徑
    ├── style.css
    └── app.js
```

### 打包注意事項
- `plugin.json` **必須位於 `.zip` 根目錄**（不可多包一層父資料夾）。
- **Zip Slip 防護**：任何包含 `..`、跨目錄跳脫或絕對路徑的壓縮條目，上傳時都會被拒絕（`400 InvalidPluginPackage`）。

---

## 3. `plugin.json` 完整欄位規範

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "default_locale": "en",
  "description": "顯示於「設定 → 外掛管理」的簡短說明。",
  "entry": "ui/index.html",
  "nav_label": "My Plugin",
  "nav_label_i18n": {
    "en": "My Plugin",
    "zh_TW": "我的外掛"
  },
  "nav_icon": "fas fa-puzzle-piece",
  "handler": "my-plugin",
  "protected_tags": [
    {
      "role": "MANAGEMENT",
      "name": "My Plugin Admin",
      "color": "#8b5cf6",
      "hidden": true
    }
  ]
}
```

| 欄位 | 必填 | 說明 |
| --- | --- | --- |
| `id` | 是 | 小寫英數字與連字號（`^[a-z0-9][a-z0-9-]{0,62}$`）。 |
| `name` | 是 | 外掛管理頁顯示名稱。 |
| `version` | 是 | 版號字串（顯示於管理介面並用於靜態資源快取控制）。 |
| `entry` | 否 | 外掛前端進入點相對路徑（預設 `index.html`）。 |
| `nav_label` | 否 | 頂部導覽列預設顯示名稱。 |
| `nav_label_i18n` | 否 | 依語系代碼對應導覽列名稱的物件，隨系統切換語系即時變換。 |
| `nav_icon` | 否 | FontAwesome 圖示類別（例如 `fas fa-graduation-cap`）。 |
| `default_locale` | 否 | 預設語系代碼（預設 `en`）。 |
| `handler` | 否 | 對應 `PluginService` 註冊之 `PluginHandler` ID。 |
| `protected_tags` | 否 | 安裝時自動建立並鎖定於 `T_PLUGIN_TAG` 的保護標籤清單。 |

### 固定保護標籤 (`protected_tags`)
- **`role`**：`"MANAGEMENT"`（外掛管理員識別標籤）或 `"PLUGIN_ROOT"`。
- **`hidden`**：設為 `true` 時，該標籤會於一般文件側邊欄與標籤選擇器隱藏（`GET /api/tag/list` 預設過濾），僅於「標籤管理 (`#/tagadmin`)」顯示供管理員指派 ACL 權限。
- **鎖定生命週期**：
  - 外掛安裝期間（無論**啟用或停用**），受保護標籤皆**不可刪除、不可更名、不可更改隱藏狀態**（`400 ProtectedPluginTag`）。
  - 僅於**解除安裝外掛**後才解除保護鎖，並保留既有 `T_TAG` 與文件關聯。

---

## 4. 多語系 (i18n) 標準規範

1. **宿主傳遞語系**：
   - `PluginView.js` 載入外掛 `iframe` 時會附帶 `?v=<version>&lang=<currentLang>`，且當使用者於主系統切換語系時，會即時重新載入對應語系。
2. **外掛讀取 `lang` 參數**：
   ```js
   const urlParams = new URLSearchParams(window.location.search);
   const currentLang = urlParams.get('lang') || 'en';
   ```
3. **標準 `locale/*.json` 與錯誤碼對應**：
   - 務必提供 `locale/en.json` 作為 Fallback 字典。
   - 將後端錯誤碼（`{ "type": "ValidationError", "message": "..." }`）對應至 `error.<type>` 鍵值，實現前後端完整多語系化。
4. **主系統支援的 16 種語系代碼**：
   `en`、`zh_TW`、`zh_CN`、`ja`、`vi`、`de`、`fr`、`es`、`pt`、`pt_BR`、`it`、`pl`、`ru`、`el`、`sq_AL`、`sr`。

---

## 5. 通用資料儲存 API (`T_PLUGIN_DATA`)

| HTTP 方法與路徑 | 說明 |
| --- | --- |
| `GET /api/plugin/{id}/data/{collection}` | 列出指定集合的 JSON 資料。 |
| `GET /api/plugin/{id}/data/{collection}/{key}` | 依 `key` 取得單筆 JSON 資料。 |
| `PUT /api/plugin/{id}/data/{collection}/{key}` | 新增或更新 JSON（`Content-Type: application/json`）。 |
| `DELETE /api/plugin/{id}/data/{collection}/{key}` | 刪除單筆資料（僅擁有者或管理員）。 |

- **機密欄位自動遮罩**：非系統管理員查詢時，凡 JSON 鍵名以 `_`、`secret_` 或 `correct_` 開頭的欄位皆會被遞迴過濾，不會回傳至瀏覽器。

---

## 6. 進階後端擴充 (`PluginHandler` SPI)

實作 `com.sismics.docs.core.util.plugin.PluginHandler` 並於 `PluginService` 註冊：

```java
public interface PluginHandler {
    String getId();
    PluginResult handleAction(String method, String action, PluginContext context) throws Exception;
    default Optional<String> findTagReferenceReason(String tagId) {
        return Optional.empty();
    }
}
```

- **`PluginContext`**：提供 `userId`、`username`、`userDisplayName`、`isAdmin()`、`targetIdList`（使用者與群組 ID）、`queryParams` 與 `requestBody`。
- **`PluginResult`**：支援 `PluginResult.ok(JsonObject)`、`PluginResult.error(status, JsonObject)` 與 `PluginResult.binary(bytes, contentType, fileName)`。
- **`findTagReferenceReason(tagId)`**：若外掛資料有引用使用者標籤，覆寫此方法後，主系統刪除該標籤時會自動阻擋並回傳 `400 TagInUseByPlugin`。

---

## 7. 資安與開發最佳實踐檢查表

- **Content Security Policy (CSP)**：外掛靜態資源會強制加上 `X-Content-Type-Options: nosniff` 與 `Content-Security-Policy`（禁止載入外部未授權腳本與內嵌 `<script>`）。
  - 所有 JS/CSS 必須打包在 `.zip` 內，避免使用外部 CDN 或 HTML inline 事件（如 `onclick="..."`）。
- **`GET` 請求不可帶 `Content-Type: application/json`**：前端使用 `fetch()` 時，僅在有 `body`（`POST`/`PUT`）時才附加 `Content-Type: application/json`，否則會觸發 `415 Unsupported Media Type`。
- **防權限提升**：外掛內建授權功能應透過 `PluginTagDao.isManagementProtectedTag(tagId)` 禁止操作 `MANAGEMENT` 保護標籤。
- **過濾停用帳號與無效群組**：授權清單僅列出 `disableTimestamp == null` 的帳號與未刪除群組，且後端寫入前必須二次驗證。
- **稽核軌跡**：重要管理操作請透過 `AuditLogUtil.create(...)` 寫入系統稽核日誌。
