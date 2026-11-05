# FormosaBearV (v2.01)

[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

**FormosaBearV** is an open-source, lightweight document management system for individuals and businesses.

> **Fork Notice**: This repository is forked from [https://github.com/fmaass/teedy-docs](https://github.com/fmaass/teedy-docs) (version `1.12`), modernized and maintained independently starting from version **`2.01`**.

## Key Upgrades in v2.01

- **Java 21 (LTS)** runtime and compilation baseline
- **Eclipse Jetty 12.1.14 (`ee10`)** & **Jakarta Servlet 6.0**
- **Jersey 3.1.9** (Jakarta RESTful Web Services 3.1)
- **H2 Database 2.3.232** with automatic legacy DDL transformation (`H2Dialect`)
- **AngularJS 1.8.3** & **jQuery 3.7.1** with updated third-party frontend libraries
- **Bundled Pan-CJK Font (`Noto Sans CJK TC`)** for out-of-the-box Traditional/Simplified Chinese, Japanese, Korean, and Latin rendering in DOCX, PPTX, ODT, and PDF thumbnails
- **Enhanced Tag Browser & Admin UI** with expandable/collapsible hierarchical trees and sub-folder/document count badges
- **Web-Managed Storage Settings** (`file.delete`, `file.duplicate`, and one-time immutable initialization lock for `file.encrypt` in **Settings -> Configuration**; see [`STORAGE.md`](STORAGE.md) for physical storage architecture details)

## Features

- Responsive web user interface
- Optical Character Recognition (OCR) via Tesseract
- LDAP authentication
- Support for image, PDF, ODT, DOCX, and PPTX files
- Video file support and thumbnail extraction
- Full-text search with suggestions and highlighting (Apache Lucene)
- Dublin Core metadata and custom user-defined metadata
- Document review workflow system
- 256-bit AES encryption of stored files
- File versioning
- Nested tag system
- Document import from email (EML format) and automatic IMAP inbox scanning
- User and hierarchical group permission system
- Two-factor authentication (TOTP)
- Audit log, comments, and per-user storage quotas
- Document sharing via URL
- RESTful Web API and outgoing Webhooks

## Running with Docker

A `Dockerfile` is included to build a self-contained container image with OpenJDK 21, Jetty 12.1 (`ee10`), Tesseract OCR, `ffmpeg`, and `mediainfo`, listening on port `8080`.

If no PostgreSQL configuration is provided, an embedded H2 2.3 database is used (suitable for testing only; use PostgreSQL for production deployments).

- **Default admin credentials**: username `admin`, password `admin` (change immediately in production).
- **Data volume**: mount a persistent volume to `/data`.
- **Base URL**: configure `DOCS_BASE_URL` (e.g., `https://docs.example.com`) for external link generation.

### Environment Variables

- **General**
  - `DOCS_BASE_URL`: Base URL used by the application when generating external links.
  - `DOCS_GLOBAL_QUOTA`: Default storage quota (in bytes) applied to users.
  - `DOCS_BCRYPT_WORK`: Work factor for bcrypt password hashing (`4` to `31`, default `10`).
- **Admin Initialization**
  - `DOCS_ADMIN_EMAIL_INIT`: Initial email address for the `admin` user.
  - `DOCS_ADMIN_PASSWORD_INIT`: Initial bcrypt password hash for the `admin` user (escape `$` with `$$` in Docker Compose).
- **Database**
  - `DATABASE_URL`: JDBC connection string for Hibernate (e.g., `jdbc:postgresql://db:5432/teedy`).
  - `DATABASE_USER`: Database username.
  - `DATABASE_PASSWORD`: Database password.
  - `DATABASE_POOL_SIZE`: Connection pool size (default `10`).
- **Language**
  - `DOCS_DEFAULT_LANGUAGE`: Default OCR/document language (`eng`, `fra`, `ita`, `deu`, `spa`, `por`, `pol`, `rus`, `ukr`, `ara`, `hin`, `chi_sim`, `chi_tra`, `jpn`, `tha`, `kor`, `nld`, `tur`, `heb`, `hun`, `fin`, `swe`, `lav`, `dan`).
- **Email (SMTP)**
  - `DOCS_SMTP_HOSTNAME`: SMTP server hostname.
  - `DOCS_SMTP_PORT`: SMTP server port.
  - `DOCS_SMTP_USERNAME`: SMTP username.
  - `DOCS_SMTP_PASSWORD`: SMTP password.

### Docker Compose Example (PostgreSQL)

```yaml
services:
  teedy-server:
    build: .
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DOCS_BASE_URL: "https://docs.example.com"
      DOCS_ADMIN_EMAIL_INIT: "admin@example.com"
      DOCS_ADMIN_PASSWORD_INIT: "$$2a$$05$$PcMNUbJvsk7QHFSfEIDaIOjk1VI9/E7IPjTKx.jkjPxkx2EOKSoPS"
      DATABASE_URL: "jdbc:postgresql://teedy-db:5432/teedy"
      DATABASE_USER: "teedy_db_user"
      DATABASE_PASSWORD: "teedy_db_password"
      DATABASE_POOL_SIZE: "10"
    volumes:
      - ./docs/data:/data
    depends_on:
      - teedy-db

  teedy-db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: "teedy_db_user"
      POSTGRES_PASSWORD: "teedy_db_password"
      POSTGRES_DB: "teedy"
    volumes:
      - ./docs/db:/var/lib/postgresql/data
```

## Building and Running from Source

### Prerequisites

- **JDK 21**
- **Apache Maven 3.8+**
- **Node.js & npm** (with `grunt-cli` for production frontend bundling)
- **Tesseract 4+**, **ffmpeg**, and **mediainfo**

### Project Structure

- `docs-core`: Persistence layer, JPA entities, DAOs, full-text indexing, and document processing services
- `docs-web-common`: Shared JAX-RS filters, security providers, and REST utilities
- `docs-web`: REST API resources and AngularJS 1.8.3 web application

### Build All Modules

From the root directory:

```bash
mvn clean install -DskipTests
```

### Run Development Server (Jetty 12.1 EE10)

From the `docs-web` directory:

```bash
mvn jetty:run
```

### Build Production WAR

From the `docs-web` directory:

```bash
mvn -Pprod -DskipTests clean install
```

The deployable WAR archive (`docs-web-2.01.war`) will be generated in `docs-web/target/`.

## License

Released under the terms of the GNU General Public License v2.0 (GPL-2.0). See `COPYING` for full license text.
