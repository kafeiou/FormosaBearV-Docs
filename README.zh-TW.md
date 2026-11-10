[ English ](README.md) | [ 繁體中文 ](README.zh-TW.md)

# FormosaBearV (v2.03)

[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

**FormosaBearV** 是一款專為個人與企業打造的開源、輕量級現代化文件管理系統 (Document Management System, DMS)。

> **分支說明 (Fork Notice)**：本專案分支出自 [https://github.com/sismics/docs](https://github.com/sismics/docs) (版本 `1.12`)，自 **`2.01`** 起進行全面現代化架構重構與獨立維護。

## v2.03 重大升級亮點

- **全面安全防禦強化**：修復 LDAP Injection 與儲存型 XSS 漏洞，預設管理員密碼升級為 `superSecure`。
- **免安裝可攜伺服器優化**：啟動腳本全面純英文化（根除 Windows 終端亂碼），嚴格校驗 Java 21+ 執行環境，內建 Jetty 12 開箱即用配置。
- **標準化檔名編碼**：檔案下載與預覽全面相容 RFC 5987 / RFC 6266 UTF-8 國際化檔名規範。

## v2.02 重大升級亮點

- **完整系統備份與還原**：串流即時匯出標準 ZIP 備份，支援 H2 / PostgreSQL 跨資料庫遷移與安全復原機制。
- **Office 文件多頁互動式預覽**：整合 Mozilla PDF.js 引擎，支援在瀏覽器中對 PPTX、DOCX、ODT 進行線上翻頁檢視。
- **階層式標籤權限管理**：支援標籤父子權限繼承、中斷繼承安全邊界與階層式樹狀選擇器。
- **全域禁止下載開關**：提供系統層級開關，可在保留線上閱讀的同時嚴禁檔案下載。
- **免安裝可攜版打包機制**：提供一鍵打包腳本，自動將 Jetty 12 與 WAR 打包為隨身碟即開即用的綠色發行包。

## v2.01 重大升級亮點

- 全面提升至 **Java 21 (LTS)**、**Eclipse Jetty 12.1 (`ee10`)**、**Jersey 3.1** 與 **H2 Database 2.3**。
- 前端架構升級至 **AngularJS 1.8.3** 與 **jQuery 3.7.1**。
- 內建開源 Pan-CJK 字型（`Noto Sans CJK TC`），支援中日韓字元排版渲染。
- 支援增強型樹狀標籤導航與 Web 端儲存設定維護。

## 核心功能特色

- 響應式 Web 操作介面（支援桌機與行動裝置）
- Tesseract 光學文字辨識 (OCR)
- LDAP 企業帳號整合認證
- 支援圖片、PDF、ODT、DOCX 與 PPTX 檔案格式
- 支援影片格式上傳與自動縮圖擷取
- Apache Lucene 全文搜尋（支援即時關鍵字建議與內文醒目標示）
- Dublin Core 標準中繼資料與使用者自訂欄位
- 文件審核與簽核工作流 (Review Workflow)
- 256-bit AES 磁碟檔案儲存串流加密
- 檔案多版本控制 (Versioning)
- 多層次階層式標籤系統
- 電子郵件匯入 (EML) 與 IMAP 收件匣自動定時掃描
- 使用者與群組階層式權限系統
- 雙因素身分驗證 (2FA / TOTP)
- 操作稽核日誌 (Audit Log)、文件留言討論與個人儲存配額管理
- 透過安全分享連結對外公開文件
- RESTful Web API 與外傳 Webhooks 事件通知

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

本建置會利用 `frontend-maven-plugin` 自動下載輕量 Node.js 執行階段至 `target/`，打包並壓縮前端資源至 `dist/`，並封裝出乾淨、可直接部署的 WAR 檔（`docs-web-2.03.war`，不含原始 `src/` 目錄）—— **主機上只需安裝 JDK 21 與 Maven 即可獨立完成**：

```text
docs-web/target/docs-web-2.03.war
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
target/FormosaBearV-docs-2.03-portable.zip
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
docker build -t formosabearv-docs:2.03 .
```

專案內建 `Dockerfile`，包含 OpenJDK 21、Jetty 12.1 (`ee10`)、Tesseract OCR、`ffmpeg` 與 `mediainfo`，預設接聽 `8080` 連接埠。

若未指定 PostgreSQL 連線資訊，系統預設使用嵌入式 H2 2.3 資料庫（建議僅供本機測試評估使用；正式生產環境強烈建議搭配外部 PostgreSQL）。

- **預設管理員帳號密碼**：帳號 `admin`，密碼 `superSecure`（正式環境請於首次登入後立即變更）。
- **資料儲存磁碟卷**：請將持久化磁碟掛載至容器內的 `/data`。
- **基礎 URL**：設定 `DOCS_BASE_URL`（例如 `https://docs.example.com`）以供系統產生正確的對外文件連結。

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
