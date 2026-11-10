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
- **Authentication Source Tagging & Dual-Password Prevention**:
  - Added `USE_AUTH_SOURCE_C varchar(20) default 'INTERNAL'` column to `T_USER` (database version upgraded to 36 via [dbupdate-036-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-036-0.sql)).
  - Implemented 4-layer defense in depth to prevent simultaneous valid LDAP and local passwords:
    1. [InternalAuthenticationHandler.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/InternalAuthenticationHandler.java): Rejects local password authentication when `auth_source == "LDAP"`.
    2. [LdapAuthenticationHandler.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/LdapAuthenticationHandler.java): Automatically marks authenticated users with `auth_source = "LDAP"`, while `admin` is permanently locked to `INTERNAL`.
    3. [UserResource.java](file:///W:/developer/project/github/teedydocs/docs-web/src/main/java/com/sismics/docs/rest/resource/UserResource.java): Forbids password self-service changes or recovery requests for LDAP accounts (`LdapUserPasswordChangeForbidden` & `LdapUserPasswordResetForbidden`).
    4. Admin user edit supports "Convert to Local Account" dialog and API, unlinking LDAP and resetting a new local password.
  - Frontend UI displays `[LDAP]` badges on user list and edit forms, hiding inapplicable password inputs.

### ✨ New Features & System Improvements
- **Web-Configurable Base URL**:
  - Added `ConfigType.BASE_URL` with management in Admin Settings > General Settings (default `http://localhost:8080`).
  - Eliminated strict requirement on `DOCS_BASE_URL` environment variable.
  - Built-in guardrails: automatically prepends `http://` if protocol is missing and strips trailing slashes to prevent double-slash email link URLs.
- **Email Sender Name & Branding**:
  - Added `ConfigType.SMTP_FROM_NAME` with management in Admin Settings > SMTP Settings (default `FormosaBearV`).
  - Fully replaced hardcoded `Teedy` branding across sender display name, subject prefix, and email template header banner.
  - Implemented smart fallback: SMTP Sender Name ➔ Theme Application Name ➔ Default `FormosaBearV`.
- **Dynamic Email Localization**:
  - Outbound email subjects and templates dynamically resolve according to system default language (e.g. Traditional Chinese `chi_tra` ➔ `zh_TW`, subject `FormosaBearV - 請重新設置密碼`).

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

### 🔒 Dependency Vulnerability Mitigation & Security Upgrades
- **Log4j 1.x EOL Remediation**:
  - Completely replaced `log4j:log4j:1.2.17` with `ch.qos.reload4j:reload4j:1.2.25` and updated the SLF4J binding to `org.slf4j:slf4j-reload4j:1.7.36`.
  - Fully remediated all known Log4j 1.2.17 CVEs (including CVE-2019-17571 SocketServer RCE, CVE-2022-23307 Chainsaw, CVE-2022-23305 JDBCAppender, CVE-2022-23302 JMSSink, and CVE-2021-4104 JMSAppender).
  - Excluded transitive `commons-logging` and `log4j:1.2.12` from `subethasmtp-wiser` in root `pom.xml`, ensuring complete removal of legacy `log4j:log4j` from the dependency tree.
- **Backend Drivers & Framework Upgrades**:
  - **Jsoup HTML Parser**: Upgraded from `1.15.4` to `1.23.2` to resolve XSS and attribute parsing vulnerabilities.
  - **Apache Commons Compress**: Upgraded from `1.22` to `1.27.1` (remediates CVE-2024-26308, CVE-2024-25710, CVE-2023-42503 DoS and OOM).
  - **Commons IO**: Upgraded from `2.11.0` to `2.18.0` (remediates CVE-2024-47554 DoS).
  - **Google Guava**: Upgraded from `31.1-jre` to `33.3.1-jre` (remediates CVE-2023-2976 temporary directory security issue).
  - **Apache PDFBox**: Upgraded from `2.0.29` to `2.0.32` (remediates CVE-2024-34750 infinite loop DoS).
  - **FreeMarker Template Engine**: Upgraded from `2.3.32` to `2.3.34` (remediates CVE-2024-36779 SSRF and file disclosure).
  - **Square OkHttp**: Upgraded from `4.10.0` to `4.12.0` (remediates CVE-2023-3782 certificate bypass).
  - **Bouncy Castle**: Migrated from legacy `bcprov-jdk15on:1.70` to modern `bcprov-jdk18on:1.78.1` (remediates CVE-2024-29857 RSA DoS and CVE-2023-33201 LDAP injection).
  - **PostgreSQL JDBC Driver**: Upgraded from `42.6.0` to `42.7.11`.
  - **Apache Directory LDAP Client API**: Upgraded from `2.1.7` to `2.1.8`.
  - **Jersey REST Client**: Upgraded from `3.1.9` to `3.1.10` across all Jersey artifacts.
- **Frontend Toolchain Modernization & Deprecated CLI Removal**:
  - **Complete Removal of Deprecated `docs-importer`**:
    - Removed the unmaintained legacy standalone Node.js CLI utility `docs-importer/` which depended on the archived `request: ^2.83.0` library.
    - Cleaned up obsolete UI navigation states and partials (`settings.html`, `app.js`, `settings.fileimporter.html`) and orphaned localization entries across 11 languages, eliminating 12 unfixable inherited vulnerabilities (`boom`, `cryptiles`, `hawk`, `sntp`, `har-validator`, etc.).
  - **Frontend Build Core & Dependency Upgrades (`docs-web`)**:
    - Upgraded `grunt` from `^1.0.1` to `^1.6.1` (remediates CVE-2020-7729 arbitrary code execution and CVE-2022-0436 race condition / file overwrite).
    - Leveraged npm `overrides` to safely lock transitive build dependencies:
      - `async`: Forced to `^2.6.4` (remediates CVE-2021-43138 prototype pollution).
      - `clean-css`: Forced to `^4.2.4` (remediates CVE-2020-7789 ReDoS).
      - `js-yaml`: Forced to `^3.14.1` (remediates CVE-2020-7712 prototype pollution).
      - `hosted-git-info`: Forced to `^2.8.9` (remediates CVE-2021-23362 ReDoS).
      - `underscore.string`: Forced to `^3.3.6`.
      - `uuid`: Forced to `^9.0.1`.
      - `lodash`: Locked to `4.18.1` (remediates CVE-2021-23337 command injection and prototype pollution).
      - `semver`: Locked to `5.7.3` (remediates CVE-2022-25883 ReDoS).
      - `qs`: Locked to `6.17.1` (remediates CVE-2022-24999 prototype pollution).
      - `tough-cookie`: Locked to `4.1.4` (remediates CVE-2023-26136 prototype pollution).
      - `form-data`: Locked to `4.0.6` (remediates CVE-2020-7608 header injection).
      - `minimist`: Upgraded to `1.2.8` (remediates CVE-2021-44906).
      - `getobject`: Upgraded to `1.1.1` (remediates CVE-2020-28282).
      - `underscore`: Upgraded to `1.13.8` (remediates CVE-2021-23358).
      - `json-schema`: Upgraded to `0.4.0` (remediates CVE-2021-3918).

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
