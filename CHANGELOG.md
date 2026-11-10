[ English ](CHANGELOG.md) | [ 繁體中文 ](CHANGELOG.zh-TW.md)

# FormosaBearV Changelog

This document details the notable changes, security enhancements, architecture improvements, and bug fixes for **FormosaBearV** since the modern architecture modernization baseline (`v2.01`).

---

## [v2.03] - 2026-11-10

> **Baseline**: Git Tag `2.02` (`53cc6936`) ➜ `v2.03`

### 🛡️ Security Hardening
- **CWE-90: LDAP Injection Fix** (`0f464186` / [Issue #797](https://github.com/sismics/docs/issues/797))
  - **Description**: When LDAP authentication was enabled, user-supplied usernames were directly interpolated into the LDAP search filter using plain `String.replace()` without escaping. An attacker could supply `username=*` to expand the filter to `(uid=*)`, matching all directory entries and binding as arbitrary users if passwords matched.
  - **Resolution**:
    - Integrated RFC 4515 compliant `FilterEncoder.encodeFilterValue()` to strictly escape LDAP metacharacters (backslash `\`, asterisk `*`, parentheses `()`, and NUL bytes).
    - Added `validateUsername()` regex validation (`[a-zA-Z0-9_@.-]+`) at the login endpoint in `UserResource` as an extra defense-in-depth measure.
- **gh-2432: Frontend jQuery XSS Risk Mitigation** (`cb09c3c8` / upstream `ee172985`)
  - Addressed potential cross-site scripting risks during HTML tag parsing in `docs-web/src/main/webapp/src/lib/jquery.js`.
- **CWE-79: Stored XSS Defense on File Download & MIME Confusion** (`05fb4474` / [Issue #796](https://github.com/sismics/docs/issues/796))
  - **Description**: Uploaded files with active MIME types (e.g., HTML, SVG, XHTML containing JavaScript) could be rendered inline by browsers in the application origin, executing arbitrary scripts. Furthermore, session `auth_token` was accessible via `document.cookie`.
  - **Resolution**:
    - Introduced `isActiveMime()` helper in [FileResource.java](file:///W:/developer/project/github/teedydocs/docs-web/src/main/java/com/sismics/docs/rest/resource/FileResource.java) to detect active types (`text/html`, `image/svg+xml`, `text/xml`, `application/xhtml+xml`, etc.).
    - For active MIME types, remapped `Content-Type` to `application/octet-stream` and forced `Content-Disposition: attachment`.
    - Added security header `X-Content-Type-Options: nosniff` to block MIME sniffing by browsers.
    - Added cache prevention headers (`Cache-Control: private, no-cache, no-store`).
    - Standardized filename encoding according to RFC 5987 / RFC 6266 with `filename*=UTF-8''` support, eliminating garbled non-ASCII/CJK filename downloads across all operating systems.
- **Default Administrator Credential Hardening**:
  - Changed default administrator password from `admin` to `superSecure`.
  - Updated database initialization script [dbupdate-000-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-000-0.sql) with a 10-round BCrypt hash (`$2a$10$iGnrUPOrJfn.cowMyfrSfOow5vD0nxHK8JAyIfxbztA8RtLL/IhmC`).
  - Synchronized [Constants.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/constant/Constants.java#L23), test suites, and 5-language documentation.

### 🚀 Portable Server & Runtime Improvements
- **Console Output ASCII Standardization**:
  - Converted all console messages in [start.bat](file:///W:/developer/project/github/teedydocs/scripts/templates/start.bat), [stop.bat](file:///W:/developer/project/github/teedydocs/scripts/templates/stop.bat), [start.sh](file:///W:/developer/project/github/teedydocs/scripts/templates/start.sh), and [stop.sh](file:///W:/developer/project/github/teedydocs/scripts/templates/stop.sh) to pure English ASCII, completely preventing Windows CMD Big5/CP950 code page corruption.
- **Windows CMD Syntax Bugfix (`was unexpected at this time`)**:
  - Removed unescaped parenthesis characters within `if (...)` batch blocks that previously triggered premature block termination.
- **Strict Java 21+ Validation**:
  - Replaced fragile path-guessing loops with strict, prioritized inspection of `%JAVA_HOME%\bin\java.exe` and system `PATH`.
  - Added clean regex matching for Java 21+ and friendly Adoptium OpenJDK 21 download guidance if Java is absent.
- **Jetty 12 Module Pre-configuration**:
  - Packaging scripts ([scripts/package-portable.ps1](file:///W:/developer/project/github/teedydocs/scripts/package-portable.ps1) / [package-portable.sh](file:///W:/developer/project/github/teedydocs/package-portable.sh)) automatically initialize `start.d/ee10-deploy.ini` and `start.d/http.ini`, preventing `ERROR : No enabled jetty modules found!`.
- **Automatic Unzipped Directory Synchronization**:
  - The portable packaging script automatically extracts and syncs the distribution directly into `target/FormosaBearV-docs-2.03-portable/` alongside the ZIP archive for immediate local testing.

### 📦 Project Versioning & Artifacts
- All Maven POMs upgraded to `<version>2.03</version>`.
- Frontend `package.json` and `package-lock.json` updated to `"version": "2.0.3"`.
- Backend properties and metadata (`config.properties`, `AppResource.java`, `BackupService.java`) updated to `2.03`.
- Documentation synchronized across [README.md](file:///W:/developer/project/github/teedydocs/README.md), [README.zh-TW.md](file:///W:/developer/project/github/teedydocs/README.zh-TW.md), [STORAGE.md](file:///W:/developer/project/github/teedydocs/STORAGE.md), [STORAGE.zh-TW.md](file:///W:/developer/project/github/teedydocs/STORAGE.zh-TW.md), and [README.txt](file:///W:/developer/project/github/teedydocs/scripts/templates/README.txt).

---

## [v2.02] - 2026-11-04

> **Baseline**: Git Tag `2.01` ➜ Git Tag `2.02` (`53cc6936`)

### ✨ New Features
- **Full System Backup & Restore**:
  - Streaming export of complete system backups as a standard ZIP archive (`FormosaBearV-docs_backup_YYYYMMDD_HHmmss.zip`) without consuming server temporary disk space.
  - Cross-database compatible ANSI SQL database dump (`dump.sql`) supporting seamless migration between **H2 and PostgreSQL**.
  - Packaging of physical document storage (`storage/` and `delete/` directories) and system settings (`config.json`).
  - Transactional rollback, Zip Slip path traversal defenses, high-risk confirmation guardrails, and background Lucene full-text index reconstruction.
- **Interactive Multi-Page Office Document Previews**:
  - Full in-browser slide-by-slide viewing for **PowerPoint (`.pptx`)** presentations and page-by-page viewing for **Word (`.docx`, `.odt`)** documents powered by Mozilla PDF.js.
  - Automatic on-the-fly multi-page PDF generation with server-side caching (`_pdf`).
  - Context menu lock, disabled drag, and suppressed print buttons.
- **Hierarchical Tag Permissions & Block Inheritance**:
  - Automatic permission inheritance from ancestor tags with optional "Block Inheritance" isolation boundaries.
  - Hierarchical tree dropdown selectors with real-time keyword filtering, breadcrumb paths, and circular dependency guardrails.
- **Hardened Download Suppression**:
  - Global download switch (`canDownload()`) enforced across document cards, table views, three-dot menus, and tag views while maintaining in-browser multi-page reading.
- **Standalone Portable Package Builder (Docker-Free)**:
  - Packaging scripts (`package-portable.bat` / `package-portable.sh`) bundling Jetty 12 and WAR into `target/FormosaBearV-docs-2.02-portable.zip`.
  - Isolated storage under local `data/` directory and 5-language quick-start manual (`README.txt`).
- **WAR Deployment Storage Persistence**:
  - Guaranteed database persistence to disk (`%APPDATA%\FormosaBearV\Docs\db\docs.mv.db` on Windows or `/var/docs/db/` on Linux).

---

## [v2.01] - 2026-10-31

> **Baseline**: Teedy 1.12 ➜ `v2.01` (Modernization baseline)

### 🚀 Technical Stack Modernization
- Upgraded baseline to **Java 21 (LTS)** runtime and compilation.
- Container upgrade to **Eclipse Jetty 12.1.14 (`ee10`)** and **Jakarta Servlet 6.0**.
- **Jersey 3.1.9** (Jakarta RESTful Web Services 3.1).
- **H2 Database 2.3.232** with legacy DDL compatibility transformer (`H2Dialect`).
- Frontend upgrade to **AngularJS 1.8.3** and **jQuery 3.7.1**.
- **Bundled Pan-CJK Font (`Noto Sans CJK TC`)** for out-of-the-box CJK rendering.
- **Enhanced Tag Browser & Admin UI** with expandable/collapsible hierarchical navigation.
- **Web-Managed Storage Settings** (`file.delete`, `file.duplicate`, `file.encrypt` initialization lock).
