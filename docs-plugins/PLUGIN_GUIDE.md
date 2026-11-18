# FormosaBearV Docs Plugin Development Guide (`3.01-SNAPSHOT`)

> **Important Note for AI Agents**: If you are an AI Agent tasked with creating, modifying, or debugging a plugin, you **MUST** first read [docs-plugins/AGENTS.md](AGENTS.md) (which contains the 10 mandatory rules and `❌ FORBIDDEN vs ✅ REQUIRED` code patterns) as well as [online-exam/README.md](online-exam/README.md) (the full-stack reference implementation walkthrough).

This guide explains how to build, localize, package, auto-seed, secure, and deploy third-party plugins for **FormosaBearV Docs** (`3.01-SNAPSHOT`+).

---

## 1. Architecture Overview & Integration Modes

FormosaBearV Docs supports two plugin integration modes:

1. **Frontend-Only Plugin + Generic JSON Data Store (`T_PLUGIN_DATA`)**:
   - Upload a `.zip` package containing `plugin.json`, static HTML/CSS/JS assets, and `locale/*.json` dictionaries.
   - Rendered inside an isolated `iframe` container at `#/plugin/<id>`.
   - Uses the built-in `/api/plugin/{id}/data/{collection}/{key}` CRUD API with automatic secret-field masking for non-admin users.
2. **Full-Stack Plugin (`PluginHandler` SPI)**:
   - Combines a `.zip` frontend package with a backend `PluginHandler` registered in `PluginService`.
   - Exposes four lifecycle and extension hooks:
     - `getId()`: Matches `"handler"` in `plugin.json`.
     - `handleAction(method, action, context)`: Handles custom business logic under `/api/plugin/{id}/action/{action}` (e.g., server-side grading, role-based access isolation, binary PDF export).
     - `findTagReferenceReason(tagId)`: Blocks accidental deletion of tags referenced by plugin data.
     - `onEnabled(installerUserId)`: Provisions initial groups, hierarchical tags, multilingual reference documents, and seed data on first enable.

---

## 2. Plugin Installation, First-Enable Provisioning & Runtime Sequence (Mermaid)

```mermaid
sequenceDiagram
    actor Admin as Administrator (admin)
    participant UI as Settings UI (#/settings/plugins)
    participant PR as PluginResource (/api/plugin)
    participant PPU as PluginPackageUtil
    participant PS as PluginService
    participant PH as PluginHandler (ExamPluginHandler)
    participant FS as FileService & FileProcessingAsyncListener

    Admin->>PR: 1. POST /api/plugin/upload (plugin-xxx.zip)
    PR->>PPU: installPackage(zipStream)
    Note over PPU: Enforce 50MB limit, 2000-entry limit,<br/>and Zip Slip checks; extract to /data/plugins/{id}
    PPU-->>PR: PluginManifest (parsed plugin.json)
    PR->>PS: registerOrUpgrade(manifest, adminId)
    Note over PS: Save T_PLUGIN (default enabled=false)<br/>Create & lock protected_tags in T_PLUGIN_TAG

    Admin->>PR: 2. POST /api/plugin/{id}/enable
    PR->>PS: onEnabled(plugin, adminId)
    PS->>PH: onEnabled(adminId)
    Note over PH: Check T_PLUGIN_DATA (demo_seeded)<br/>1. Create non-privileged groups (roleId=null)<br/>2. Create English hierarchical tags & least-privilege ACLs<br/>3. Create textbook document & 16 localized PDFs via FileService<br/>4. Create 16-language exam bound to textbook document ID
    PH->>FS: FileUtil.createFile(...) fires FileCreatedAsyncEvent
    Note over FS: Asynchronously generate thumbnails, extract PDF plain text<br/>(for the "eye" icon view), and index in Lucene
```

---

## 3. Package Directory Structure & Bundled Distribution (`docs-plugins/dist/plugin-*.zip`)

```text
my-plugin/
├── plugin.json          # Required at the root of the .zip archive
├── locale/              # Standard 16-language JSON directory
│   ├── en.json          # Required default / fallback locale
│   └── zh_TW.json       # Locale file matching host language codes
├── seed/                # (Optional) Seed files read by PluginHandler.onEnabled
│   ├── SEC-001.gift
│   └── SEC-001-i18n.json
└── ui/
    ├── index.html       # Entry HTML specified in plugin.json "entry"
    ├── style.css
    └── app.js
```

### Bundled Distribution & Auto-Seeding (`PluginSeedService`)
- `plugin.json` **must sit at the root of the `.zip` archive** using POSIX `/` path separators.
- Any `docs-plugins/dist/plugin-*.zip` file is automatically copied into `docs-core`'s `classpath:plugins/` directory during `mvn process-resources` / `mvn package`.
- **Encryption-Aware Seeding into the `plugins` Document**:
  - On a fresh database, `PluginSeedService` waits until the administrator locks the file storage encryption mode via `POST /api/app/file_encrypt` (so it never prematurely locks `FILE_ENCRYPT` to unencrypted).
  - Once locked (or on subsequent restarts/upgrades when `ConfigUtil.isFileEncryptLocked()` is `true`), `PluginSeedService` creates an admin-only `plugins` tag (`hidden = false`) and `plugins` document (with a 16-language line-by-line description), attaches any unseeded `plugin-*.zip` files, and records them in `ConfigType.PLUGINS_SEEDED`.

---

## 4. `PluginHandler` SPI & 5 Security Guardrails for Resource Provisioning

```java
public interface PluginHandler {
    String getId();
    PluginResult handleAction(String method, String action, PluginContext context) throws Exception;
    default Optional<String> findTagReferenceReason(String tagId) {
        return Optional.empty();
    }
    default void onEnabled(String installerUserId) throws Exception {
    }
}
```

When a plugin provisions groups, tags, documents, and files inside `onEnabled(installerUserId)`, it must enforce five security guardrails:
1. **No Privilege Escalation**: Newly created `Group` entities must have `roleId = null` (never `admin`). If a group or tag with the same name already exists, reuse its ID without altering its existing ACLs.
2. **Least Privilege ACLs**: Provisioned tags and documents grant `READ`/`WRITE` to `installerUserId` (`admin`) and the plugin's teacher group, and `READ`-only to the plugin's student group—never exposing them globally to all users.
3. **Safe File Extraction & Temporary File Lifecycle**: Seed files inside the plugin package must be resolved via `PluginPackageUtil.resolvePluginAsset(pluginId, relativePath)` with size limits; generated files attached to documents must use `AppContext.getInstance().getFileService().createTemporaryFile(fileName)` + `FileUtil.createFile(...)` so encryption, storage quotas, PDF plain-text extraction ("eye" icon), and Lucene indexing work seamlessly.
4. **Tag Naming Compliance**: Visible tags (`hidden = false`) must use English identifiers without spaces or colons (e.g., `OnlineExamMaterials` -> `SecurityFundamentals`) to pass `ValidationUtil.validateTagName` and support `tag:` search queries.
5. **Idempotency**: Record a completion flag (e.g., `demo_seeded = "true"`) in `T_PLUGIN_DATA` so disabling/re-enabling or upgrading never duplicates seeded entities.

---

## 5. 4-Stage Dynamic Multilingual PDF Export Pipeline

FormosaBearV Docs plugins support automatic 16-language PDF report generation that dynamically matches the user's active UI language:

1. **Stage 1 (Host `PluginView.js` injects `?lang=`)**:
   The host container loads the plugin `iframe` with `?v=<version>&lang=<currentLang>` and reloads it whenever the user switches the UI language.
2. **Stage 2 (Plugin Frontend `ui/app.js` forwards `&lang=`)**:
   The plugin reads `currentLang = new URLSearchParams(window.location.search).get('lang') || 'en'` and appends `&lang=${encodeURIComponent(currentLang)}` to all data queries and PDF download URLs.
3. **Stage 3 (Backend `PluginHandler` localizes payload via `localizeExam(data, lang)`)**:
   The handler reads `ctx.getQueryParams().getOrDefault("lang", "en")` and overlays the matching locale entry from the entity's `"i18n"` map.
4. **Stage 4 (OpenPDF + `PdfUtil.BUNDLED_CJK_FONT_FAMILY` + 16-Locale Header Map + `PluginResult.binary`)**:
   The PDF exporter looks up localized header/table labels from a 16-locale dictionary (`PDF_LABELS_I18N`), renders all text using `PdfUtil.getFontProvider().getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, ...)` (`Noto Sans CJK TC`), and streams the result back via `PluginResult.binary(pdfBytes, "application/pdf", fileName)`.

