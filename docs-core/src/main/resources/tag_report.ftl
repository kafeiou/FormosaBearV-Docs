<!DOCTYPE html>
<html lang="${labels.htmlLang}">
<head>
  <meta charset="UTF-8">
  <title>FormosaBearV Docs</title>
  <style>
    * {
      box-sizing: border-box;
    }
    body {
      margin: 0;
      padding: 0;
      background-color: #f1f5f9;
      color: #1e293b;
      font-family: "Noto Sans CJK TC", "Microsoft JhengHei", "PingFang TC", sans-serif;
      font-size: 13px;
      line-height: 1.5;
    }
    .report-container {
      width: 1040px;
      margin: 24px auto 48px auto;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
      padding: 28px 32px;
    }
    .report-header {
      border-bottom: 2px solid #1e3a5f;
      padding-bottom: 16px;
      margin-bottom: 20px;
    }
    .report-title {
      margin: 0 0 8px 0;
      font-size: 22px;
      font-weight: 700;
      color: #1e3a5f;
    }
    .report-meta {
      color: #475569;
      font-size: 12px;
    }
    .report-meta span {
      margin-right: 18px;
    }
    .stats-bar {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 24px;
      background-color: #f8fafc;
      border: 1px solid #cbd5e1;
    }
    .stats-bar td {
      width: 25%;
      padding: 12px 14px;
      border-right: 1px solid #e2e8f0;
      text-align: center;
    }
    .stats-bar td:last-child {
      border-right: none;
    }
    .stat-label {
      font-size: 11px;
      color: #64748b;
      margin-bottom: 4px;
    }
    .stat-value {
      font-size: 18px;
      font-weight: 700;
      color: #0f172a;
    }
    .tag-block {
      width: 100%;
      margin: 0 0 18px 0;
      border: 1px solid #cbd5e1;
      background-color: #ffffff;
    }
    .tag-header {
      padding: 10px 14px;
      border-bottom: 1px solid #cbd5e1;
      display: table;
      width: 100%;
    }
    .tag-header-root {
      background-color: #1e3a5f;
      color: #ffffff;
    }
    .tag-header-child {
      background-color: #e8f1fa;
      color: #1e3a5f;
    }
    .tag-header-left {
      display: table-cell;
      vertical-align: middle;
    }
    .tag-header-right {
      display: table-cell;
      vertical-align: middle;
      text-align: right;
      font-size: 12px;
    }
    .hierarchy-badge {
      display: inline-block;
      padding: 2px 8px;
      margin-right: 8px;
      font-weight: 700;
      font-size: 12px;
      border-radius: 3px;
      background-color: rgba(255, 255, 255, 0.2);
    }
    .tag-header-child .hierarchy-badge {
      background-color: #1e3a5f;
      color: #ffffff;
    }
    .tag-color-dot {
      display: inline-block;
      width: 12px;
      height: 12px;
      border-radius: 2px;
      margin-right: 6px;
      vertical-align: middle;
      border: 1px solid rgba(0, 0, 0, 0.25);
    }
    .tag-name {
      font-size: 15px;
      font-weight: 700;
      vertical-align: middle;
    }
    .doc-card {
      padding: 12px 14px;
      border-bottom: 1px solid #e2e8f0;
      background-color: #ffffff;
    }
    .doc-card:last-child {
      border-bottom: none;
    }
    .doc-header {
      margin-bottom: 6px;
    }
    .doc-order {
      display: inline-block;
      font-weight: 700;
      color: #475569;
      margin-right: 6px;
    }
    .doc-title {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
      margin-right: 10px;
    }
    .doc-meta {
      font-size: 12px;
      color: #64748b;
    }
    .doc-meta span {
      margin-right: 12px;
    }
    .doc-description {
      margin: 6px 0 8px 0;
      padding: 8px 10px;
      background-color: #f8fafc;
      border-left: 3px solid #94a3b8;
      color: #334155;
      font-size: 12px;
      white-space: pre-wrap;
    }
    .file-table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 8px;
      font-size: 12px;
    }
    .file-table th {
      background-color: #f1f5f9;
      color: #334155;
      text-align: left;
      padding: 6px 10px;
      border: 1px solid #cbd5e1;
      font-weight: 600;
    }
    .file-table td {
      padding: 6px 10px;
      border: 1px solid #e2e8f0;
      vertical-align: middle;
    }
    .btn-link {
      display: inline-block;
      padding: 3px 10px;
      border-radius: 3px;
      text-decoration: none;
      font-weight: 600;
      font-size: 12px;
    }
    .btn-preview {
      background-color: #2563eb;
      color: #ffffff;
    }
    .btn-download {
      background-color: #059669;
      color: #ffffff;
    }
    .empty-msg {
      padding: 10px 14px;
      color: #94a3b8;
      font-size: 12px;
      font-style: italic;
    }
  </style>
</head>
<body>
  <div class="report-container">
    <div class="report-header">
      <div class="report-meta">
        <span>${labels.exportedAtPrefix}${exportedAt}</span>
        <span>${labels.linkModePrefix}<#if options.linkMode == "download">${labels.downloadFile}<#else>${labels.previewFile}</#if></span>
      </div>
    </div>

    <table class="stats-bar">
      <tr>
        <td>
          <div class="stat-label">${labels.kpiSelectedTags}</div>
          <div class="stat-value">${statistics.totalTags}</div>
        </td>
        <td>
          <div class="stat-label">${labels.kpiTagDocLinks}</div>
          <div class="stat-value">${statistics.totalTagDocumentLinks}</div>
        </td>
        <td>
          <div class="stat-label">${labels.kpiUniqueDocs}</div>
          <div class="stat-value">${statistics.uniqueDocuments}</div>
        </td>
        <td>
          <div class="stat-label">${labels.kpiUniqueFiles}</div>
          <div class="stat-value">${statistics.uniqueFiles}</div>
        </td>
      </tr>
    </table>

    <#list flatTags as tag>
      <div class="tag-block" style="border-left: 6px solid ${(tag.color!'#3a87ad')?html};">
        <div class="tag-header <#if tag.depth == 0>tag-header-root<#else>tag-header-child</#if>">
          <div class="tag-header-left">
            <span class="hierarchy-badge">${tag.hierarchyCode?html}</span>
            <span class="tag-color-dot" style="background-color: ${(tag.color!'#3a87ad')?html};"></span>
            <span class="tag-name">${tag.name?html}</span>
          </div>
          <div class="tag-header-right">
            ${labels.tagDocCountPrefix}${tag.documentCount} ｜ ${labels.tagFileCountPrefix}${tag.fileCount}
          </div>
        </div>

        <#if tag.documents?size == 0>
          <div class="empty-msg">${labels.emptyTagDocs}</div>
        <#else>
          <#list tag.documents as doc>
            <div class="doc-card">
              <div class="doc-header">
                <span class="doc-order">[#${doc_index + 1}]</span>
                <span class="doc-title">${(doc.title!labels.untitledDocument)?html}</span>
                <#if options.includeDate>
                  <span class="doc-meta">
                    <#if doc.formattedCreateDate?? && doc.formattedCreateDate != ""><span>${labels.createDatePrefix}${doc.formattedCreateDate?html}</span></#if>
                    <#if doc.formattedUpdateDate?? && doc.formattedUpdateDate != ""><span>${labels.updateDatePrefix}${doc.formattedUpdateDate?html}</span></#if>
                  </span>
                </#if>
              </div>

              <#if options.includeDescription && doc.plainDescription?? && doc.plainDescription != "">
                <div class="doc-description">${doc.plainDescription?html}</div>
              </#if>

              <#if doc.files?size == 0>
                <div class="empty-msg">${labels.emptyDocFiles}</div>
              <#else>
                <table class="file-table">
                  <thead>
                    <tr>
                      <th style="width: 56px;">${labels.colOrder}</th>
                      <th>${labels.colFileName}</th>
                      <th style="width: 90px;">${labels.colVersion}</th>
                      <#if options.includeSize>
                        <th style="width: 100px;">${labels.colFileSize}</th>
                      </#if>
                      <#if options.includeDate>
                        <th style="width: 140px;">${labels.colCreateDate}</th>
                      </#if>
                      <th style="width: 110px;">${labels.colFileAction}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <#list doc.files as file>
                      <tr>
                        <td>${file_index + 1}</td>
                        <td>${file.name?html}</td>
                        <td>${file.versionDisplay?html}</td>
                        <#if options.includeSize>
                          <td>${file.formattedSize?html}</td>
                        </#if>
                        <#if options.includeDate>
                          <td>${(file.formattedCreateDate!'')?html}</td>
                        </#if>
                        <td>
                          <#if file.effectiveLinkMode == "download">
                            <a class="btn-link btn-download" href="${file.effectiveUrl?html}" target="_blank" rel="noopener">${labels.downloadFile}</a>
                          <#else>
                            <a class="btn-link btn-preview" href="${file.effectiveUrl?html}" target="_blank" rel="noopener">${labels.previewFile}</a>
                          </#if>
                        </td>
                      </tr>
                    </#list>
                  </tbody>
                </table>
              </#if>
            </div>
          </#list>
        </#if>
      </div>
    </#list>
  </div>
</body>
</html>
