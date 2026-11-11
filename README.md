[ English ](README.md) | [ 繁體中文 ](README.zh-TW.md)

# FormosaBearV (v2.04)

[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

**FormosaBearV** is an open-source, lightweight document management system for individuals and businesses.

> **Fork Notice**: This repository is forked from [https://github.com/sismics/docs](https://github.com/sismics/docs) (version `1.12`), modernized and maintained independently starting from version **`2.01`**.

## Features

- **Modern Architecture & Portable Deployment**: Built on Java 21 (LTS) and Eclipse Jetty 12, supporting standard WAR deployment, Docker containers, and a zero-install Standalone Portable Edition.
- **Multi-Format & Interactive Office Previews**: Supports images, videos, PDF, ODT, DOCX, and PPTX with in-browser multi-page previewing and bundled Pan-CJK font rendering.
- **Full-Text Search & OCR**: Powered by Apache Lucene (with instant suggestions and highlighting) and integrated Tesseract Optical Character Recognition.
- **Hierarchical Tags & Permission Inheritance**: Multi-level nested tag tree navigation with automatic ancestor permission inheritance and customizable security boundaries.
- **Granular Document & Download Security**: User/group access control, system-wide and per-document download authorization controls (allowing view-only access while preventing file exfiltration), and 256-bit AES storage encryption.
- **Enterprise Authentication**: Supports internal authentication, LDAP directory integration, and Two-Factor Authentication (TOTP).
- **Full System Backup, Restore & Migration**: Streaming full-system ZIP backup and restore with cross-database migration support between H2 and PostgreSQL.
- **Document Lifecycle & Collaboration**: File versioning, Dublin Core and custom metadata, document review workflows, comments, audit logs, per-user storage quotas, and URL sharing.
- **Automated Import & Integration**: EML email import, automated IMAP inbox scanning, customizable SMTP notifications, RESTful Web API, and outgoing Webhooks.

## Building and Running from Source

### Prerequisites

- **JDK 21**
- **Apache Maven 3.8+**
- **Tesseract 4+**, **ffmpeg**, and **mediainfo**

### Project Structure

- `docs-core`: Persistence layer, JPA entities, DAOs, full-text indexing, and document processing services
- `docs-web-common`: Shared JAX-RS filters, security providers, and REST utilities
- `docs-web`: REST API resources and AngularJS 1.8.3 web application

### Build All Modules & Generate Deployable WAR

From the project root directory, run:

```bash
mvn clean package -DskipTests
```
*(or `mvn clean install -DskipTests`)*

This build uses `frontend-maven-plugin` to automatically download a portable Node.js runtime into `target/`, bundle and minify the frontend assets into `dist/`, and generate the clean, deployable WAR archive (`docs-web-2.04.war`, without raw `src/` directories) — **requiring only JDK 21 and Maven on the host machine**:

```text
docs-web/target/docs-web-2.04.war
```

### Build Standalone Portable Edition (No Docker Required)

Run the one-click packaging script from the project root:

```bash
# Windows (or simply double-click package-portable.bat)
.\package-portable.bat

# Linux / macOS
chmod +x package-portable.sh && ./package-portable.sh
```

This automatically downloads the base Jetty 12 ZIP into `tools/jetty/` if absent, compiles the WAR, assembles all modules, and outputs:

```text
target/FormosaBearV-docs-2.04-portable.zip
```

Simply unzip this package on any computer with Java 21 and run `start.bat` or `./start.sh`!

### Run Development Server (Jetty 12.1 EE10)

From the `docs-web` directory:

```bash
mvn jetty:run
```

---

## Running with Docker

> **Important Prerequisite**: Because the `Dockerfile` packages `docs-web/target/docs-web-*.war` into the container (`/app/webapps/docs.war`), **you must first compile the project with Maven** (`mvn clean package -DskipTests`) before building the Docker image (`docker build` or `docker compose up --build`).

```bash
# Step 1: Build the WAR archive with Maven first
mvn clean package -DskipTests

# Step 2: Build the Docker image (or use docker compose)
docker build -t formosabearv-docs:2.04 .
```

A `Dockerfile` is included to build a self-contained container image with OpenJDK 21, Jetty 12.1 (`ee10`), Tesseract OCR, `ffmpeg`, and `mediainfo`, listening on port `8080`.

If no PostgreSQL configuration is provided, an embedded H2 2.3 database is used (suitable for testing only; use PostgreSQL for production deployments).

- **Default admin credentials**: username `admin`, password `superSecure` (change immediately in production).
- **Data volume**: mount a persistent volume to `/data`.
- **Base URL**: defaults to `http://localhost:8080`, directly configurable in Admin Settings under General Settings, or customizable via `DOCS_BASE_URL`.

### Environment Variables

- **General**
  - `DOCS_BASE_URL`: Base URL used by the application when generating external links.
  - `DOCS_GLOBAL_QUOTA`: Default storage quota (in bytes) applied to users.
  - `DOCS_BCRYPT_WORK`: Work factor for bcrypt password hashing (`4` to `31`, default `10`).
- **Admin Initialization**
  - `DOCS_ADMIN_EMAIL_INIT`: Initial email address for the `admin` user.
  - `DOCS_ADMIN_PASSWORD_INIT`: Initial bcrypt password hash for the `admin` user (escape `$` with `$$` in Docker Compose).
- **Database**
  - `DATABASE_URL`: JDBC connection string for Hibernate (e.g., `jdbc:postgresql://db:5432/formosabearv`).
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

## Docker Compose Examples

In the following examples some passwords are exposed in cleartext. This was done in order to keep the examples simple. We strongly encourage you to use variables with an `.env` file or other means to securely store your passwords.

### Default, using PostgreSQL

```yaml
version: '3'
services:
# FormosaBearV Application
  formosabearv-docs:
    image: kafeiou/formosabearv-docs:latest
    restart: unless-stopped
    ports:
      # Map internal port to host
      - "8080:8080"
    environment:
      # Base url to be used
      DOCS_BASE_URL: "https://docs.example.com"
      # Set the admin email
      DOCS_ADMIN_EMAIL_INIT: "admin@example.com"
      # Set the admin password (in this example: "superSecure")
      DOCS_ADMIN_PASSWORD_INIT: "$$2a$$05$$PcMNUbJvsk7QHFSfEIDaIOjk1VI9/E7IPjTKx.jkjPxkx2EOKSoPS"
      # Setup the database connection. "formosabearv-db" is the hostname
      # and "formosabearv" is the name of the database the application
      # will connect to.
      DATABASE_URL: "jdbc:postgresql://formosabearv-db:5432/formosabearv"
      DATABASE_USER: "formosabearv_db_user"
      DATABASE_PASSWORD: "formosabearv_db_password"
      DATABASE_POOL_SIZE: "10"
    volumes:
      - ./docs/data:/data
    networks:
      - docker-internal
      - internet
    depends_on:
      - formosabearv-db

# DB for FormosaBearV
  formosabearv-db:
    image: postgres:16-alpine
    restart: unless-stopped
    expose:
      - 5432
    environment:
      POSTGRES_USER: "formosabearv_db_user"
      POSTGRES_PASSWORD: "formosabearv_db_password"
      POSTGRES_DB: "formosabearv"
    volumes:
      - ./docs/db:/var/lib/postgresql/data
    networks:
      - docker-internal

networks:
  # Network without internet access. The db does not need
  # access to the host network.
  docker-internal:
    driver: bridge
    internal: true
  internet:
    driver: bridge
```

### Using the internal database (only for testing)

```yaml
version: '3'
services:
# FormosaBearV Application
  formosabearv-docs:
    image: kafeiou/formosabearv-docs:latest
    restart: unless-stopped
    ports:
      # Map internal port to host
      - "8080:8080"
    environment:
      # Base url to be used
      DOCS_BASE_URL: "https://docs.example.com"
      # Set the admin email
      DOCS_ADMIN_EMAIL_INIT: "admin@example.com"
      # Set the admin password (in this example: "superSecure")
      DOCS_ADMIN_PASSWORD_INIT: "$$2a$$05$$PcMNUbJvsk7QHFSfEIDaIOjk1VI9/E7IPjTKx.jkjPxkx2EOKSoPS"
    volumes:
      - ./docs/data:/data
```

## License

Released under the terms of the GNU General Public License v2.0 (GPL-2.0). See `COPYING` for full license text.
