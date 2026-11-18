# FormosaBearV Docs Plugin Development Guide

This guide explains how to build, localize, package, secure, and deploy third-party plugins for **FormosaBearV Docs** (v3.0.0+).

---

## 1. Architecture Overview

FormosaBearV Docs provides two plugin integration modes:

1. **Frontend-Only Plugin + Generic JSON Storage (`T_PLUGIN_DATA`)**:
   - Upload a `.zip` package containing `plugin.json`, static HTML/CSS/JS assets, and `locale/*.json`.
   - Rendered inside a sandboxed host container route `#/plugin/<id>` (`iframe`).
   - Uses `/api/plugin/{id}/data/{collection}/{key}` for persistent JSON storage with automatic secret-field masking for non-admins.
2. **Full-Stack Plugin (`PluginHandler` SPI)**:
   - Pairs a `.zip` frontend package with a Java `PluginHandler` registered in `PluginService`.
   - Routes custom backend logic and binary streams (e.g., PDF export) through `/api/plugin/{id}/action/{action}`.

---

## 2. Package Structure & Packaging Rules

```text
my-plugin/
├── plugin.json          # Required manifest at ZIP root
├── locale/              # Standard i18n JSON files
│   ├── en.json          # Required default/fallback locale
│   └── zh_TW.json       # Additional locales matching host codes
└── ui/
    ├── index.html       # Entry referenced by plugin.json "entry"
    ├── style.css
    └── app.js
```

### Packaging Rules
- `plugin.json` **must** sit at the root of the `.zip` archive (not nested inside a parent folder).
- **Zip Slip Protection**: Any archive entry containing `..`, backslashes escaping the target directory, or absolute paths is rejected with `400 InvalidPluginPackage`.

---

## 3. `plugin.json` Manifest Reference

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "default_locale": "en",
  "description": "Short description shown in Settings -> Plugins.",
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

| Field | Required | Description |
| --- | --- | --- |
| `id` | Yes | Lowercase alphanumeric + hyphens (`^[a-z0-9][a-z0-9-]{0,62}$`). |
| `name` | Yes | Display name in plugin management. |
| `version` | Yes | Semantic version string (shown in admin UI and used for asset cache-busting). |
| `entry` | No | Relative path inside `.zip` to HTML entry (defaults to `index.html`). |
| `nav_label` | No | Fallback top navbar label. |
| `nav_label_i18n` | No | Map of `<localeCode> -> <localizedLabel>` for dynamic navbar switching. |
| `nav_icon` | No | FontAwesome class (e.g. `fas fa-graduation-cap`). |
| `default_locale` | No | Default locale passed if host locale is unavailable (defaults to `en`). |
| `handler` | No | ID of a registered `PluginHandler` in `PluginService`. |
| `protected_tags` | No | Tags auto-created and locked in `T_PLUGIN_TAG`. |

### Protected Tags (`protected_tags`)
- **`role`**: `"MANAGEMENT"` (top-level plugin admin tag) or `"PLUGIN_ROOT"`.
- **`hidden`**: When `true`, the tag is hidden from the regular document sidebar and tag pickers (`GET /api/tag/list`), while remaining visible in Tag Administration (`#/tagadmin`) for ACL management.
- **Locking Lifecycle**:
  - While the plugin is installed (whether **enabled or disabled**), protected tags cannot be deleted, renamed, or have their visibility changed (`400 ProtectedPluginTag`).
  - Only upon **uninstalling** the plugin is the lock removed (the `T_TAG` row and document associations are preserved).

---

## 4. Internationalization (i18n) Standard

1. **Host-to-Plugin Locale Passing**:
   - `PluginView.js` loads the iframe with `?v=<version>&lang=<currentLang>` and automatically reloads when the user switches language (`$rootScope.$on('$translateChangeSuccess', ...)`).
2. **Reading `lang` in Your Plugin**:
   ```js
   const urlParams = new URLSearchParams(window.location.search);
   const currentLang = urlParams.get('lang') || 'en';
   ```
3. **Standard `locale/*.json` & Error Code Mapping**:
   - Always provide `locale/en.json` as the fallback dictionary.
   - Map backend error types under `"error": { "<ErrorType>": "..." }` so structured backend errors (`{ "type": "ValidationError", "message": "..." }`) translate cleanly in the UI.
4. **Supported Host Locale Codes**:
   `en`, `zh_TW`, `zh_CN`, `ja`, `vi`, `de`, `fr`, `es`, `pt`, `pt_BR`, `it`, `pl`, `ru`, `el`, `sq_AL`, `sr`.

---

## 5. Generic Data Storage API (`T_PLUGIN_DATA`)

| Method & Path | Description |
| --- | --- |
| `GET /api/plugin/{id}/data/{collection}` | List JSON items in `collection`. |
| `GET /api/plugin/{id}/data/{collection}/{key}` | Fetch single item by `key`. |
| `PUT /api/plugin/{id}/data/{collection}/{key}` | Upsert JSON body (`Content-Type: application/json`). |
| `DELETE /api/plugin/{id}/data/{collection}/{key}` | Delete item (owner or admin only). |

- **Secret-Field Masking**: For non-admin callers, any JSON key starting with `_`, `secret_`, or `correct_` is automatically stripped recursively before returning to the browser.

---

## 6. Backend `PluginHandler` SPI (Advanced)

Implement `com.sismics.docs.core.util.plugin.PluginHandler` and register it in `PluginService`:

```java
public interface PluginHandler {
    String getId();
    PluginResult handleAction(String method, String action, PluginContext context) throws Exception;
    default Optional<String> findTagReferenceReason(String tagId) {
        return Optional.empty();
    }
}
```

- **`PluginContext`** provides `userId`, `username`, `userDisplayName`, `isAdmin()`, `targetIdList` (user ID + group IDs), `queryParams`, and `requestBody`.
- **`PluginResult`** supports `PluginResult.ok(JsonObject)`, `PluginResult.error(status, JsonObject)`, and `PluginResult.binary(bytes, contentType, fileName)`.
- **`findTagReferenceReason(tagId)`**: If your plugin references user-created tags, override this method so `DELETE /api/tag/{id}` blocks deletion with `400 TagInUseByPlugin` while referenced.

---

## 7. Security & Best Practices Checklist

- **Content Security Policy (CSP)**: Plugin assets are served with `X-Content-Type-Options: nosniff` and `Content-Security-Policy: default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; script-src 'self'; object-src 'none'; frame-ancestors 'self'`.
  - Bundle all JS/CSS inside your `.zip` — **do not** load external CDNs or use inline `<script>` / `onclick="..."` handlers.
- **No `Content-Type: application/json` on `GET`**: Only attach `Content-Type: application/json` to `fetch()` calls when `options.body` is present (`POST`/`PUT`), otherwise JAX-RS returns `415 Unsupported Media Type`.
- **Privilege Escalation Guard**: Never allow non-admin plugin managers to bind or modify ACLs on `MANAGEMENT` protected tags (`PluginTagDao.isManagementProtectedTag(tagId)`).
- **Active Users & Groups Only**: When querying users/groups for permission assignment, exclude disabled/deleted users (`disableTimestamp == null`) and deleted groups, and re-validate on the backend before persisting.
- **Audit Logging**: Record state-changing management actions via `AuditLogUtil.create(...)`.
