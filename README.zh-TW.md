[ English ](README.md) | [ 繁體中文 ](README.zh-TW.md)

# FormosaBearV (v2.02)

[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

**FormosaBearV** 是一款專為個人與企業打造的開源、輕量級現代化文件管理系統 (Document Management System, DMS)。

> **分支說明 (Fork Notice)**：本專案分支出自 [https://github.com/sismics/docs](https://github.com/sismics/docs) (版本 `1.12`)，自 **`2.01`** 起進行全面現代化架構重構與獨立維護。

## v2.02 重大升級亮點

- **完整系統備份與還原 (Full System Backup & Restore)**：
  - 以串流方式即時匯出標準 ZIP 備份檔（`FormosaBearV-docs_backup_YYYYMMDD_HHmmss.zip`），完全不佔用伺服器暫存硬碟空間。
  - 具備跨資料庫相容性的 ANSI SQL 資料庫匯出檔 (`dump.sql`)，完美支援 **H2 與 PostgreSQL** 之間的無縫資料遷移。
  - 匯出完整系統設定檔 (`config.json`)，妥善保存檔案加密模式、重複檔名規則、回收區軟刪除與下載權限設定。
  - 完整打包實體儲存檔案（`storage/` 與 `delete/` 目錄），支援還原完整性驗證。
  - 具備交易自動復原 (Transactional Rollback)、Zip Slip 路徑穿越攻擊防護、高風險操作確認提示，以及還原後背景自動重建 Lucene 全文搜尋索引。
- **Office 文件多頁互動式預覽 (Multi-Page Office Previews)**：
  - 採用 Mozilla PDF.js 核心引擎，支援在瀏覽器中對 **PowerPoint 簡報 (`.pptx`)** 進行逐頁翻頁閱讀，以及對 **Word 文件 (`.docx`, `.odt`)** 進行逐頁檢視。
  - 自動於背景產生高畫質多頁 PDF 預覽暫存 (`_pdf`)，後續檢視秒開不卡頓。
  - 預覽視窗內建安全保護：防右鍵功能表選單鎖定、禁止拖曳與隱藏列印按鈕。
- **強化的下載限制與安全防護 (Hardened Download Suppression)**：
  - 全域禁止下載權限開關 (`canDownload()`)，全面貫穿文件卡片、表格清單、右鍵操作選單與標籤視圖。
  - 提供系統層級管理設定，可全面關閉檔案下載功能，同時保留完整的線上多頁閱讀能力。
- **階層式標籤權限與樹狀選擇器 (Hierarchical Tag Permissions & Tree Selector)**：
  - 支援標籤父子繼承，子標籤自動繼承祖先標籤之讀取/寫入權限，並支援「中斷繼承」鎖定專屬權限。
  - 標籤管理、標籤編輯與文件標籤彈窗全面導入**階層式樹狀下拉選擇器**，支援節點層層展開/收合、完整路徑麵包屑顯示、關鍵字即時搜尋過濾，以及防止循環相依之防呆機制。
- **WAR 部署儲存持久化 (WAR Deployment Storage Persistence)**：
  - 修正原版預設記憶體 H2 資料庫問題，確保每次 WAR 重新部署時資料庫永久寫入實體硬碟（Windows 位於 `%APPDATA%\FormosaBearV\Docs\db\docs.mv.db`，Linux 位於 `/var/docs/db/`）。
  - 詳細架構與目錄結構請參閱：[`STORAGE.zh-TW.md`](STORAGE.zh-TW.md)。
  - 巨量資料備份維運請參閱：[`BACKUP_GUIDE.zh-TW.md`](BACKUP_GUIDE.zh-TW.md)。

## v2.01 重大升級亮點

- 全面提升至 **Java 21 (LTS)** 執行階段與編譯基準
- 升級至 **Eclipse Jetty 12.1.14 (`ee10`)** 與 **Jakarta Servlet 6.0**
- 升級至 **Jersey 3.1.9** (Jakarta RESTful Web Services 3.1)
- 升級至 **H2 Database 2.3.232**，具備舊版 DDL 自動相容轉換機制 (`H2Dialect`)
- 升級前端架構至 **AngularJS 1.8.3** 與 **jQuery 3.7.1**
- **內建開源 Pan-CJK 字型 (`Noto Sans CJK TC`)**：開箱即用支援繁體中文、簡體中文、日文、韓文與英數排版渲染於 DOCX、PPTX、ODT 與 PDF 縮圖
- **增強型標籤導航與管理介面**：支援樹狀可展開/收合層級導航與子資料夾/文件計數徽章
- **Web 端儲存設定維護**（可在 **設置 -> 設定** 管理 `file.delete`、`file.duplicate`，以及 `file.encrypt` 首次初始化鎖定）

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

本建置會利用 `frontend-maven-plugin` 自動下載輕量 Node.js 執行階段至 `target/`，打包並壓縮前端資源至 `dist/`，並封裝出乾淨、可直接部署的 WAR 檔（`docs-web-2.02.war`，不含原始 `src/` 目錄）—— **主機上只需安裝 JDK 21 與 Maven 即可獨立完成**：

```text
docs-web/target/docs-web-2.02.war
```

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
docker build -t formosabearv-docs:2.02 .
```

專案內建 `Dockerfile`，包含 OpenJDK 21、Jetty 12.1 (`ee10`)、Tesseract OCR、`ffmpeg` 與 `mediainfo`，預設接聽 `8080` 連接埠。

若未指定 PostgreSQL 連線資訊，系統預設使用嵌入式 H2 2.3 資料庫（建議僅供本機測試評估使用；正式生產環境強烈建議搭配外部 PostgreSQL）。

- **預設管理員帳號密碼**：帳號 `admin`，密碼 `admin`（正式環境請於首次登入後立即變更）。
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
