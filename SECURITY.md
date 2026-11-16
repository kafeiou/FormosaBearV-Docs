# Security Policy / 資安政策

## Supported Versions / 支援版本

| Version | Supported |
| ------- | --------- |
| 2.07+   | ✅ Yes    |
| <= 2.06 | ❌ Please upgrade to the latest release |

## Reporting a Vulnerability / 回報安全性漏洞

If you discover a security vulnerability in **FormosaBearV**, please **do not** disclose it via public GitHub issues.

如果您發現 **FormosaBearV** 的任何安全性漏洞，請**不要**直接建立公開的 GitHub Issue，請透過以下管道私下回報：

- **GitHub Private Vulnerability Reporting (Preferred)**:  
  Open a private advisory at:  
  `https://github.com/kafeiou/FormosaBearV-Docs/security/advisories/new`

### What to Include / 回報建議內容
- Affected version(s) and deployment type (WAR, Portable, Docker, H2/PostgreSQL)
- Affected endpoint(s) or component(s)
- Step-by-step reproduction instructions or Proof of Concept (PoC)
- Potential security impact and any suggested remediation

We aim to acknowledge new reports within **72 hours** and coordinate a fix before public disclosure.

---

## Reverse Proxy Security Headers / 反向代理安全標頭建議

When deploying **FormosaBearV** behind a reverse proxy (**Nginx** or **Apache httpd**), it is strongly recommended to add HTTP security headers at the proxy layer to provide defense-in-depth (e.g., mitigating clickjacking, MIME sniffing, and XSS).

將 **FormosaBearV** 部署於反向代理（**Nginx** 或 **Apache httpd**）後方時，強烈建議在代理層補上以下 HTTP 安全標頭以強化縱深防禦（防止點擊劫持、MIME 嗅探與 XSS 等風險）。

> **Note / 注意事項**：
> - `Strict-Transport-Security` (HSTS) should **only** be enabled when serving over HTTPS.
> - Because the frontend uses AngularJS 1.8.3 and PDF.js, if you enable `Content-Security-Policy`, allow `'self'`, `'unsafe-inline'`, `'unsafe-eval'` (or test thoroughly with PDF preview / colorpicker / blob URLs before enforcing). Start with `Content-Security-Policy-Report-Only` if unsure.

### Nginx Example (`server` / `location` block)

```nginx
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "SAMEORIGIN" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
# Enable only on HTTPS / 僅在 HTTPS 啟用：
# add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

### Apache `httpd` Example (`VirtualHost` / `.htaccess`, requires `mod_headers`)

```apache
Header always set X-Content-Type-Options "nosniff"
Header always set X-Frame-Options "SAMEORIGIN"
Header always set Referrer-Policy "strict-origin-when-cross-origin"
Header always set Permissions-Policy "geolocation=(), microphone=(), camera=()"
# Enable only on HTTPS / 僅在 HTTPS 啟用：
# Header always set Strict-Transport-Security "max-age=31536000; includeSubDomains"
```
