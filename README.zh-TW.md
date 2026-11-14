[ English ](README.md) | [ 繁體中文 ](README.zh-TW.md)

# FormosaBearV (v2.06)

[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

**FormosaBearV** 是一款專為個人與企業打造的開源、輕量級現代化文件管理系統 (Document Management System, DMS)。

> **分支說明 (Fork Notice)**：本專案分支出自 [https://github.com/sismics/docs](https://github.com/sismics/docs) (版本 `1.12`)，自 **`2.01`** 起進行全面現代化架構重構與獨立維護。

## 核心功能特色

- **現代化架構與跨平台免安裝部署**：基於 Java 21 (LTS) 與 Eclipse Jetty 12 打造，支援標準 WAR、Docker 容器化與免安裝可攜綠色版（Portable Edition）即開即用。
- **多格式文件與 Office 線上互動預覽**：支援圖片、影片、PDF、ODT、DOCX 與 PPTX 檔案，整合線上逐頁翻頁預覽、動態防洩漏浮水印覆蓋層與內建中日韓 (Pan-CJK) 字型渲染。
- **全文檢索與光學文字辨識 (OCR)**：內建 Apache Lucene 全文搜尋（支援即時建議與關鍵字醒目標示）與 Tesseract OCR 自動文字擷取。
- **階層式標籤、權限繼承與報表匯出**：支援多層次樹狀標籤導航、父子標籤權限自動繼承、獨立權限邊界控管，以及階層式標籤報表與歸檔匯出（PDF、HTML、Excel 與 JSON ZIP）。
- **細粒度文件、分享與下載安全管控**：提供使用者與群組權限、全域與單一文件級別的下載權限控管、公開分享連結匯出限制（可限制僅供線上閱覽、禁止下載檔案或匯出 PDF），以及 256-bit AES 儲存串流加密。
- **企業級身分驗證與離職治理**：支援本地認證、多組 LDAP 企業目錄備援整合（含 LDAPS / STARTTLS 與連線測試）、雙因素驗證 (2FA / TOTP)、帳號停用即時連線撤銷，以及員工離職資產與權限一鍵交接。
- **完整系統備份、還原與跨庫遷移**：支援後台一鍵串流匯出完整系統 ZIP 備份與還原，並支援 H2 與 PostgreSQL 跨資料庫無縫遷移。
- **文件生命週期與協作工作流**：支援檔案多版本控制與自訂版本標記、Dublin Core 與自訂中繼資料、審核簽核流程 (Review Workflow)、留言討論、操作稽核日誌、個人儲存配額管理（含無上限配額）與安全外部連結分享。
- **自動化匯入與系統整合**：支援 EML 郵件匯入、IMAP 收件匣自動掃描、SMTP 郵件通知、RESTful Web API 與 Webhooks 事件推播。
- **多國語系介面**：支援 14 種國際語言完整在地化（英文、法文、德文、西班牙文、義大利文、葡萄牙文、波蘭文、俄文、希臘文、阿爾巴尼亞文、日文、越南文、繁體中文與簡體中文）。

## 從原始碼編譯與建置 (Building from Source)

### 環境需求

- **JDK 21**
- **Apache Maven 3.8+**
- **Tesseract 4+**、**ffmpeg** 與 **mediainfo**

### 專案模組架構

- `docs-core`：資料持久層、JPA 實體、DAO、全文檢索索引與文件處理服務
- `docs-web-common`：共用 JAX-RS 篩選器、安全提供者與 REST 工具類別
- `docs-web`：REST API 端點與 AngularJS 1.8.3 前端網頁應用程式

### 建置所有模組並產生可部署的 WAR 檔

在專案根目錄下執行：

```bash
mvn clean package -DskipTests
```
*(或執行 `mvn clean install -DskipTests`)*

本建置會利用 `frontend-maven-plugin` 自動下載輕量 Node.js 執行階段至 `target/`，打包並壓縮前端資源至 `dist/`，並封裝出乾淨、可直接部署的 WAR 檔（`docs-web-2.06.war`，不含原始 `src/` 目錄）—— **主機上只需安裝 JDK 21 與 Maven 即可獨立完成**：

```text
docs-web/target/docs-web-2.06.war
```

### 打包免安裝可攜綠色版（無需 Docker）

在專案根目錄執行一鍵打包腳本：

```bash
# Windows 環境（或直接雙擊 package-portable.bat）
.\package-portable.bat

# Linux / macOS 環境
chmod +x package-portable.sh && ./package-portable.sh
```

此腳本會自動讀取 `tools/jetty/` 專案基底 Jetty 12 ZIP（缺少時自動從 Maven 中央倉庫下載），自動編譯 WAR 並組裝所有模組與腳本，最終產出：

```text
target/FormosaBearV-docs-2.06-portable.zip
```

解壓後只要電腦有安裝 Java 21，雙擊 `start.bat`（或執行 `./start.sh`）即可直接秒速啟動！

### 啟動本機開發伺服器 (Jetty 12.1 EE10)

於 `docs-web` 目錄下執行：

```bash
mvn jetty:run
```

---

## 使用 Docker 運行

> **重要前提**：由於 `Dockerfile` 會將 `docs-web/target/docs-web-*.war` 封裝至容器中 (`/app/webapps/docs.war`)，因此在建置 Docker 映像檔 (`docker build` 或 `docker compose up --build`) 之前，**必須先在主機執行 Maven 編譯** (`mvn clean package -DskipTests`)。

```bash
# 第一步：先以 Maven 編譯打包 WAR 檔
mvn clean package -DskipTests

# 第二步：建置 Docker 映像檔（或直接使用 docker compose）
docker build -t formosabearv-docs:2.06 .
```

專案內建 `Dockerfile`，包含 OpenJDK 21、Jetty 12.1 (`ee10`)、Tesseract OCR、`ffmpeg` 與 `mediainfo`，預設接聽 `8080` 連接埠。

若未指定 PostgreSQL 連線資訊，系統預設使用嵌入式 H2 2.3 資料庫（建議僅供本機測試評估使用；正式生產環境強烈建議搭配外部 PostgreSQL）。

- **預設管理員帳號密碼**：帳號 `admin`，密碼 `superSecure`（正式環境請於首次登入後立即變更）。
- **資料儲存磁碟卷**：請將持久化磁碟掛載至容器內的 `/data`。
- **基礎 URL**：預設為 `http://localhost:8080`，可直接於後台「系統設定 > 一般設定」維護，亦可透過 `DOCS_BASE_URL` 環境變數自訂。

### 常見環境變數一覽

- **通用設定**
  - `DOCS_BASE_URL`：系統產生外部公開分享連結時所依據的 Base URL。
  - `DOCS_GLOBAL_QUOTA`：套用於所有使用者的預設儲存配額（位元組 byte）。
  - `DOCS_BCRYPT_WORK`：密碼雜湊 bcrypt 計算權重（`4` 至 `31`，預設 `10`）。
- **管理員初始化**
  - `DOCS_ADMIN_EMAIL_INIT`：`admin` 帳號的初始電子信箱。
  - `DOCS_ADMIN_PASSWORD_INIT`：`admin` 帳號的初始 bcrypt 雜湊密碼（在 Docker Compose 中若有 `$` 請以 `$$` 轉義）。
- **資料庫連線**
  - `DATABASE_URL`：Hibernate JDBC 連線字串（例如 `jdbc:postgresql://db:5432/formosabearv`）。
  - `DATABASE_USER`：資料庫連線使用者帳號。
  - `DATABASE_PASSWORD`：資料庫連線密碼。
  - `DATABASE_POOL_SIZE`：資料庫連線池大小（預設 `10`）。
- **語言支援**
  - `DOCS_DEFAULT_LANGUAGE`：預設 OCR 與文件語言（`eng`, `fra`, `ita`, `deu`, `spa`, `por`, `pol`, `rus`, `ukr`, `ara`, `hin`, `chi_sim`, `chi_tra`, `jpn`, `tha`, `kor`, `nld`, `tur`, `heb`, `hun`, `fin`, `swe`, `lav`, `dan`）。
- **電子郵件 (SMTP)**
  - `DOCS_SMTP_HOSTNAME`：SMTP 伺服器主機名稱。
  - `DOCS_SMTP_PORT`：SMTP 伺服器連接埠。
  - `DOCS_SMTP_USERNAME`：SMTP 帳號。
  - `DOCS_SMTP_PASSWORD`：SMTP 密碼。

## Docker Compose 配置範例

以下範例為求展示清晰而包含明文密碼，正式環境強烈建議搭配 `.env` 檔案或機密管理工具妥善管理敏感金鑰。

### 預設使用 PostgreSQL

```yaml
version: '3'
services:
# FormosaBearV 應用程式
  formosabearv-docs:
    image: kafeiou/formosabearv-docs:latest
    restart: unless-stopped
    ports:
      # 將內部連接埠對應至宿主機
      - "8080:8080"
    environment:
      # 外部基礎 URL
      DOCS_BASE_URL: "https://docs.example.com"
      # 管理員初始電子信箱
      DOCS_ADMIN_EMAIL_INIT: "admin@example.com"
      # 管理員初始密碼（本例密碼為: "superSecure"）
      DOCS_ADMIN_PASSWORD_INIT: "$$2a$$05$$PcMNUbJvsk7QHFSfEIDaIOjk1VI9/E7IPjTKx.jkjPxkx2EOKSoPS"
      # 資料庫連線配置。"formosabearv-db" 為同網路的主機名稱
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

# FormosaBearV 專屬 PostgreSQL 資料庫
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
  # 內部專用網路（無網際網路連線，確保資料庫安全性）
  docker-internal:
    driver: bridge
    internal: true
  internet:
    driver: bridge
```

### 使用嵌入式 H2 資料庫（僅供測試評估）

```yaml
version: '3'
services:
# FormosaBearV 應用程式
  formosabearv-docs:
    image: kafeiou/formosabearv-docs:latest
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DOCS_BASE_URL: "https://docs.example.com"
      DOCS_ADMIN_EMAIL_INIT: "admin@example.com"
      DOCS_ADMIN_PASSWORD_INIT: "$$2a$$05$$PcMNUbJvsk7QHFSfEIDaIOjk1VI9/E7IPjTKx.jkjPxkx2EOKSoPS"
    volumes:
      - ./docs/data:/data
```

## 授權條款 (License)

本專案依據 **GNU General Public License v2.0 (GPL-2.0)** 條款發布，完整授權內容請參閱 `COPYING` 檔案。
