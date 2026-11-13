[ English ](CHANGELOG.md) | [ 繁體中文 ](CHANGELOG.zh-TW.md)

# FormosaBearV Changelog

This document details the notable changes, security enhancements, architecture improvements, and bug fixes for **FormosaBearV** since the modern architecture modernization baseline (`v2.01`).

---

## [v2.05] - 2026-11-12

> **Baseline**: `v2.04` (`3462d002`) ➜ `v2.05`

### ✨ New Features & System Improvements
- **Multi-LDAP Directory Authentication & Priority Failover (`T_LDAP_CONFIG`, DB v39)**:
  - Added the `T_LDAP_CONFIG` table via [dbupdate-039-0.sql](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/resources/db/update/dbupdate-039-0.sql) with JPA entity [LdapConfig.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/model/jpa/LdapConfig.java) and [LdapConfigDao.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/dao/LdapConfigDao.java), automatically migrating any existing single-LDAP settings from `T_CONFIG` during upgrade and registering `T_LDAP_CONFIG` in [BackupService.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/service/BackupService.java) for full-system backup/restore.
  - Upgraded [LdapAuthenticationHandler.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/LdapAuthenticationHandler.java) to iterate through all enabled LDAP servers in ascending priority order (`LDC_ORDER_N`). If a user is not found, credentials do not match, or a directory server is unreachable, authentication transparently falls back to the next configured LDAP server (while preserving RFC 4515 filter escaping and immediate blocking of deactivated local accounts).
  - Upgraded `GET /api/app/config_ldap` and `POST /api/app/config_ldap` in [AppResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/AppResource.java) to support multi-server JSON array payloads (`servers` / `servers_json`) while remaining backward-compatible with legacy single-server form parameters.
  - Redesigned the LDAP Settings UI ([settings.ldap.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.ldap.html), [SettingsLdap.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/settings/SettingsLdap.js)) to allow adding, editing, enabling/disabling, reordering (`#1`, `#2`, ...), and removing multiple LDAP servers.
- **Document Preview Watermark Overlay with Configurable English Date Format (`PREVIEW_WATERMARK_ENABLED` & `PREVIEW_WATERMARK_DATE_FORMAT`)**:
  - Added `PREVIEW_WATERMARK_ENABLED` and `PREVIEW_WATERMARK_DATE_FORMAT` in [ConfigType.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/constant/ConfigType.java) and [ConfigUtil.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-core/src/main/java/com/sismics/docs/core/util/ConfigUtil.java), exposed via `GET /api/app` and `POST /api/app/config_watermark` in [AppResource.java](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/java/com/sismics/docs/rest/resource/AppResource.java).
  - Added a non-interactive (`pointer-events: none`) repeating diagonal SVG watermark overlay across PDF canvas previews and image previews in both internal and shared views ([FileModalView.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/document/FileModalView.js), [file.view.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/file.view.html)).
  - Displays `<username> <date>` (falling back to `Guest` for anonymous shared links) where the date is **always formatted in English (`en-US` month/day names)** regardless of browser locale, with customizable format patterns (e.g. `dd MMM yyyy`, `MMM dd, yyyy`, `yyyy-MM-dd`, `dd MMMM yyyy HH:mm`) and live preview in General Settings ([settings.config.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.config.html), [SettingsConfig.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/settings/SettingsConfig.js)).
- **Multilingual Support for Japanese (`ja`) and Vietnamese (`vi`)**:
  - Added complete Japanese ([ja.json](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/ja.json), [angular-locale_ja.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/angular-locale_ja.js)) and Vietnamese ([vi.json](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/vi.json), [angular-locale_vi.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/locale/angular-locale_vi.js)) language packs with 100% key parity (723 translation keys).
  - Integrated `ja` and `vi` into client-side routing and automatic browser language detection ([app.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/app.js), [share/app.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/share/app.js)).
  - Added `日本語` and `Tiếng Việt` to language selectors in [index.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/index.html) and [share.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/share.html).
  - Added Japanese and Vietnamese relative time formatting dictionaries to [angular.timeago.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/lib/angular.timeago.js).
- **Footer Attribution URL & Taiwan Yushan Login Background**:
  - Updated `footer_sismics` attribution hyperlink across all 14 locale resource files to point to `https://kafeiou.pw`.
  - Updated the default login background image ([background.jpg](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/resources/image/background.jpg)) to a landscape photograph of Taiwan's Mount Yushan (CC0 1.0 Public Domain).
- **Maintenance & Repository Cleanup**:
  - Removed obsolete snapshot build artifact `build/docs-NoFileEncrypt_1.12-SNAPSHOT.war`.

### 🐛 Bug Fixes
- **Account Settings & User Management Password Update Fix**:
  - Fixed an issue in [SettingsAccount.js](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/app/docs/controller/settings/SettingsAccount.js) where `$scope.user = {}` was uninitialized, causing AngularJS `ng-if` to create an isolated child scope and send an empty payload to `POST /api/user`, which resulted in silent failures where the old password was retained despite receiving a success response.
  - Added form `ng-submit` bindings across [settings.account.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.account.html) and [settings.user.edit.html](file:///W:/developer/project/github/FormosaBearV-Docs/docs-web/src/main/webapp/src/partial/docs/settings.user.edit.html) to support keyboard Enter submissions.
  - Added proper form `$setPristine()` calls and error alert callbacks to ensure visual feedback accurately reflects operation outcomes.

---

## [v2.04] - 2026-11-11

> **Baseline**: `v2.03` (`1a8bd912`) ➜ `v2.04`

### ✨ New Features & System Improvements
- **Document Download Authorization Matrix (`DOC_ALLOW_DOWNLOAD_B`, DB v37)** (`ea6d5ba9`, `95d3d14c`):
  - Added `DOC_ALLOW_DOWNLOAD_B` column to `T_DOCUMENT` via [dbupdate-037-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-037-0.sql) (defaulting to `TRUE`).
  - Document creators, users with direct or tag-inherited document `WRITE` permission, users with `WRITE` permission on any tag attached to the document, and `admin` can toggle `allow_download` per document.
  - Enforced a unified download authorization matrix across single-file downloads (`GET /api/file/{id}/data` without `size` parameter), batch ZIP exports (`GET /api/file/zip`), and the frontend UI (`canDownload`), preventing unauthorized raw file exfiltration while keeping in-browser document and image previews (`size=web` / `thumb` / `preview`) accessible to read-only users.
- **Employee Offboarding, Account Deactivation Security & One-Click Asset/Permission Handover**:
  - **Immediate Session Revocation & Login Blocking**: Deactivating a user account immediately revokes all active authentication tokens (`AuthenticationTokenDao.deleteByUserId`), blocks active sessions in [TokenBasedSecurityFilter.java](file:///W:/developer/project/github/teedydocs/docs-web-common/src/main/java/com/sismics/util/filter/TokenBasedSecurityFilter.java), blocks LDAP sign-in in [LdapAuthenticationHandler.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/authentication/LdapAuthenticationHandler.java), and hides deactivated accounts from ACL autocomplete searches (`UserCriteria.active`).
  - **Asset Summary & Deletion Guard**: `GET /api/user/{username}` returns `owned_document_count`, `owned_tag_count`, and `owned_file_count`. Attempting to delete a user who still owns documents, tags, or files is blocked with `UserHasAssets` (`HTTP 400`) to prevent accidental data loss.
  - **One-Click Asset & ACL Handover (`POST /api/user/{username}/transfer`)**: Added one-click transfer in User Management to hand over all owned documents (`DocumentDao.transferOwnership`), tags (`TagDao.transferOwnership`), physical/encrypted files and storage quota accounting (`FileUtil.transferFileStorage` & `FileDao.transferOwnership`), active workflow steps (`RouteStepDao.transferActiveSteps`), and **all granted ACL permissions** (`AclDao.copyUserAcls`, ensuring permissions granted by `admin` or third parties on tags/documents are also inherited by the successor).
- **User-Customizable File Version Label (`FIL_VERSION_LABEL_C`, DB v38) & Version History Enhancements**:
  - Added `FIL_VERSION_LABEL_C varchar(50)` to `T_FILE` via [dbupdate-038-0.sql](file:///W:/developer/project/github/teedydocs/docs-core/src/main/resources/db/update/dbupdate-038-0.sql), preserving `1.0`/`2.0` labels for existing files while defaulting newly uploaded files and new versions to blank (`null`) so users can define custom version strings (e.g., `001`, `v1.2-draft`, `Rev.A`).
  - Updated Grid/Tag card views to display `({{ file.version_label }})` only when non-empty, and List view to show `{{ file.version_label }}`.
  - Redesigned the Version History modal ([file.versions.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/file.versions.html), [ModalFileVersions.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/controller/ModalFileVersions.js)): sorted newest-to-oldest (`orderBy: '-version'`) with `#1`/`#2` sequence numbers, a "Current / 目前版本" badge, full second-level timestamps (`yyyy-MM-dd HH:mm:ss`), and inline version label editing with a dedicated Save button.
  - Added display toggles (`showVersionMimetype` and `showVersionCreateDate`, hidden by default) in [settings.account.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/settings.account.html) and [settings.config.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/settings.config.html) to show or hide the MIME Type and Create Date columns in the version history dialog.
- **Unlimited User Storage Quota (`0` = Unlimited)** (`ea6d5ba9`):
  - Updated [FileUtil.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/util/FileUtil.java) and User Management UI so setting storage quota to `0` grants unlimited storage capacity.
- **Global Tag Creator Tooltip (`getTagTooltip`)**:
  - Added `$rootScope.getTagTooltip(tag)` in [app.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/app.js) and bound it into [SelectTag.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/directive/SelectTag.js), displaying the tag creator (`Creator: {{ creator }}` / `建立者：{{ creator }}`) on hover across Tag Management, Tag Browser, Document List/View, and the Tag Selector.
- **ACL Target Dropdown Selector (`<acl-edit>`)**:
  - Enhanced [directive.acledit.html](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/partial/docs/directive.acledit.html) and [AclEdit.js](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/app/docs/directive/AclEdit.js) with a zero-length typeahead (`typeahead-min-length="0"`) and dropdown toggle button so administrators and users can browse and click users/groups directly when assigning permissions.

### 🐛 Bug Fixes
- **ACL `READWRITE` Direct Permission Persistence**: Fixed [AclResource.java](file:///W:/developer/project/github/teedydocs/docs-web/src/main/java/com/sismics/docs/rest/resource/AclResource.java) and [AclDao.java](file:///W:/developer/project/github/teedydocs/docs-core/src/main/java/com/sismics/docs/core/dao/AclDao.java) (`hasDirectAcl`) so granting `READWRITE` (`READ` + `WRITE` requests in parallel) is not skipped when the target already has inherited or creator permissions.
- **Storage Quota Unit Conversion & Panel State**: Fixed repeated Byte/MB division when reloading user profiles in User Management, and kept user/group edit panels open after saving.
- **Input Group Button Text Visibility**: Fixed `.input-group-btn > .btn` inheriting `font-size: 0` from Bootstrap when combined with `.btn:not(...) { font-size: 1em; }` in [main.less](file:///W:/developer/project/github/teedydocs/docs-web/src/main/webapp/src/style/main.less).

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
- **SMTP Enhancements, Security Protocols & Real-Time Test Tool**:
  - **Multiple Encryption Protocols**: Added `ConfigType.SMTP_SECURITY` supporting `AUTO` (smart auto-detection), `STARTTLS`, `SSL/TLS`, and `NONE` (plaintext) modes to adapt to all public and private mail relays.
  - **Anonymous / Unauthenticated Relay Support**: Added full support for local or intranet SMTP servers without username/password authentication (e.g. `localhost:25`, Postfix). Leaving credentials blank cleanly bypasses authentication.
  - **Real-Time SMTP Test Tool**: Added a recipient test email field and "Send Test Email" button in Admin Settings > SMTP Settings (via `POST /api/app/test_smtp`). Allows administrators to immediately verify connection, TLS handshakes, and email delivery with rich feedback and detailed error diagnostics.
- **Exhaustive Localization & Missing Key Resolution**:
  - Conducted an exhaustive project-wide translation audit and eliminated all 9 genuine missing keys across all 12 localization files (covering LDAP status labels, password reset restrictions, internal account conversions, and SMTP testing dialogs).
  - Fixed raw translation placeholders (`settings.account.ldap_title`, `settings.account.ldap_desc`, and `login.password_lost_ldap_forbidden`) previously displayed to LDAP users.
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
