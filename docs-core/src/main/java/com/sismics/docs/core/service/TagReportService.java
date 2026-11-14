package com.sismics.docs.core.service;

import com.google.common.base.Strings;
import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.criteria.TagCriteria;
import com.sismics.docs.core.dao.dto.DocumentDto;
import com.sismics.docs.core.dao.dto.TagDto;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.util.PdfUtil;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.mime.MimeTypeUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for building and exporting hierarchical tag reports (JSON, Excel, HTML, PDF).
 */
public class TagReportService {

    /**
     * Export options.
     */
    public static class ExportOptions {
        private List<String> selectedTagIds = new ArrayList<>();
        private boolean includeChildren = true;
        private boolean includeDate = true;
        private boolean includeDescription = true;
        private boolean includeSize = false;
        private String linkMode = "preview";
        private String lang = "zh_TW";
        private String baseUrl = "http://localhost:8080";
        private String userId;
        private String exportedBy = "guest";
        private List<String> targetIdList = new ArrayList<>();
        private boolean adminOrPrivileged = false;

        public List<String> getSelectedTagIds() {
            return selectedTagIds;
        }

        public ExportOptions setSelectedTagIds(List<String> selectedTagIds) {
            this.selectedTagIds = selectedTagIds != null ? selectedTagIds : new ArrayList<>();
            return this;
        }

        public boolean isIncludeChildren() {
            return includeChildren;
        }

        public ExportOptions setIncludeChildren(boolean includeChildren) {
            this.includeChildren = includeChildren;
            return this;
        }

        public boolean isIncludeDate() {
            return includeDate;
        }

        public ExportOptions setIncludeDate(boolean includeDate) {
            this.includeDate = includeDate;
            return this;
        }

        public boolean isIncludeDescription() {
            return includeDescription;
        }

        public ExportOptions setIncludeDescription(boolean includeDescription) {
            this.includeDescription = includeDescription;
            return this;
        }

        public boolean isIncludeSize() {
            return includeSize;
        }

        public ExportOptions setIncludeSize(boolean includeSize) {
            this.includeSize = includeSize;
            return this;
        }

        public String getLinkMode() {
            return linkMode;
        }

        public ExportOptions setLinkMode(String linkMode) {
            this.linkMode = "download".equalsIgnoreCase(linkMode) ? "download" : "preview";
            return this;
        }

        public String getLang() {
            return lang;
        }

        public ExportOptions setLang(String lang) {
            this.lang = !Strings.isNullOrEmpty(lang) ? lang.trim() : "zh_TW";
            return this;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public ExportOptions setBaseUrl(String baseUrl) {
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                String trimmed = baseUrl.trim();
                while (trimmed.endsWith("/")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                this.baseUrl = trimmed;
            }
            return this;
        }

        public String getUserId() {
            return userId;
        }

        public ExportOptions setUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public String getExportedBy() {
            return exportedBy;
        }

        public ExportOptions setExportedBy(String exportedBy) {
            this.exportedBy = exportedBy != null ? exportedBy : "guest";
            return this;
        }

        public List<String> getTargetIdList() {
            return targetIdList;
        }

        public ExportOptions setTargetIdList(List<String> targetIdList) {
            this.targetIdList = targetIdList != null ? targetIdList : new ArrayList<>();
            return this;
        }

        public boolean isAdminOrPrivileged() {
            return adminOrPrivileged;
        }

        public ExportOptions setAdminOrPrivileged(boolean adminOrPrivileged) {
            this.adminOrPrivileged = adminOrPrivileged;
            return this;
        }
    }

    /**
     * Assembled report data.
     */
    public static class ReportData {
        private long exportedAtTimestamp;
        private String exportedAt;
        private String exportedBy;
        private String baseUrl;
        private ExportOptions options;
        private ReportStatistics statistics = new ReportStatistics();
        private List<ReportTagNode> rootTags = new ArrayList<>();
        private List<ReportTagNode> flatTags = new ArrayList<>();

        public long getExportedAtTimestamp() {
            return exportedAtTimestamp;
        }

        public String getExportedAt() {
            return exportedAt;
        }

        public String getExportedBy() {
            return exportedBy;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public ExportOptions getOptions() {
            return options;
        }

        public ReportStatistics getStatistics() {
            return statistics;
        }

        public List<ReportTagNode> getRootTags() {
            return rootTags;
        }

        public List<ReportTagNode> getFlatTags() {
            return flatTags;
        }
    }

    /**
     * Global report statistics.
     */
    public static class ReportStatistics {
        private int totalTags;
        private int totalTagDocumentLinks;
        private int uniqueDocuments;
        private int uniqueFiles;
        private long totalSize;
        private String formattedTotalSize = "0 B";

        public int getTotalTags() {
            return totalTags;
        }

        public int getTotalTagDocumentLinks() {
            return totalTagDocumentLinks;
        }

        public int getUniqueDocuments() {
            return uniqueDocuments;
        }

        public int getUniqueFiles() {
            return uniqueFiles;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public String getFormattedTotalSize() {
            return formattedTotalSize;
        }
    }

    /**
     * Tag node in report.
     */
    public static class ReportTagNode {
        private String id;
        private String parentId;
        private String hierarchyCode;
        private int depth;
        private Integer order;
        private String name;
        private String color;
        private String creator;
        private Long createTimestamp;
        private boolean container;
        private int documentCount;
        private int fileCount;
        private long totalSize;
        private String formattedTotalSize = "0 B";
        private List<ReportDocumentNode> documents = new ArrayList<>();
        private List<ReportTagNode> children = new ArrayList<>();
        private int childCounter = 0;

        public String getId() {
            return id;
        }

        public String getParentId() {
            return parentId;
        }

        public String getHierarchyCode() {
            return hierarchyCode;
        }

        public int getDepth() {
            return depth;
        }

        public Integer getOrder() {
            return order;
        }

        public String getName() {
            return name;
        }

        public String getColor() {
            return color;
        }

        public String getCreator() {
            return creator;
        }

        public Long getCreateTimestamp() {
            return createTimestamp;
        }

        public boolean isContainer() {
            return container;
        }

        public int getDocumentCount() {
            return documentCount;
        }

        public int getFileCount() {
            return fileCount;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public String getFormattedTotalSize() {
            return formattedTotalSize;
        }

        public List<ReportDocumentNode> getDocuments() {
            return documents;
        }

        public List<ReportTagNode> getChildren() {
            return children;
        }
    }

    /**
     * Document node under a tag.
     */
    public static class ReportDocumentNode {
        private String id;
        private Integer order;
        private String fileId;
        private String title;
        private String description;
        private String plainDescription;
        private String subject;
        private String identifier;
        private String publisher;
        private String format;
        private String source;
        private String type;
        private String coverage;
        private String rights;
        private String language;
        private String creator;
        private String userId;
        private boolean shared;
        private boolean allowDownload;
        private boolean canDownload;
        private Long createTimestamp;
        private Long updateTimestamp;
        private String formattedCreateDate;
        private String formattedUpdateDate;
        private List<ReportMetadataNode> metadata = new ArrayList<>();
        private List<ReportFileNode> files = new ArrayList<>();

        public String getId() {
            return id;
        }

        public Integer getOrder() {
            return order;
        }

        public String getFileId() {
            return fileId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getPlainDescription() {
            return plainDescription;
        }

        public String getSubject() {
            return subject;
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getPublisher() {
            return publisher;
        }

        public String getFormat() {
            return format;
        }

        public String getSource() {
            return source;
        }

        public String getType() {
            return type;
        }

        public String getCoverage() {
            return coverage;
        }

        public String getRights() {
            return rights;
        }

        public String getLanguage() {
            return language;
        }

        public String getCreator() {
            return creator;
        }

        public String getUserId() {
            return userId;
        }

        public boolean isShared() {
            return shared;
        }

        public boolean isAllowDownload() {
            return allowDownload;
        }

        public boolean isCanDownload() {
            return canDownload;
        }

        public Long getCreateTimestamp() {
            return createTimestamp;
        }

        public Long getUpdateTimestamp() {
            return updateTimestamp;
        }

        public String getFormattedCreateDate() {
            return formattedCreateDate;
        }

        public String getFormattedUpdateDate() {
            return formattedUpdateDate;
        }

        public List<ReportMetadataNode> getMetadata() {
            return metadata;
        }

        public List<ReportFileNode> getFiles() {
            return files;
        }
    }

    /**
     * Custom metadata node on a document.
     */
    public static class ReportMetadataNode {
        private String id;
        private String name;
        private String type;
        private String value;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * File node on a document.
     */
    public static class ReportFileNode {
        private String id;
        private Integer order;
        private String name;
        private String mimeType;
        private long size;
        private String formattedSize;
        private Integer version;
        private String versionLabel;
        private String versionDisplay;
        private String versionId;
        private Long createTimestamp;
        private String formattedCreateDate;
        private String previewUrl;
        private String downloadUrl;
        private String effectiveLinkMode;
        private String effectiveUrl;

        public String getId() {
            return id;
        }

        public Integer getOrder() {
            return order;
        }

        public String getName() {
            return name;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getSize() {
            return size;
        }

        public String getFormattedSize() {
            return formattedSize;
        }

        public Integer getVersion() {
            return version;
        }

        public String getVersionLabel() {
            return versionLabel;
        }

        public String getVersionDisplay() {
            return versionDisplay;
        }

        public String getVersionId() {
            return versionId;
        }

        public Long getCreateTimestamp() {
            return createTimestamp;
        }

        public String getFormattedCreateDate() {
            return formattedCreateDate;
        }

        public String getPreviewUrl() {
            return previewUrl;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public String getEffectiveLinkMode() {
            return effectiveLinkMode;
        }

        public String getEffectiveUrl() {
            return effectiveUrl;
        }
    }

    /**
     * Builds the hierarchical tag report data structure respecting ACLs and 3-level ordering.
     */
    @SuppressWarnings("unchecked")
    public ReportData buildReportData(ExportOptions options) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

        Date now = new Date();
        ReportData reportData = new ReportData();
        reportData.exportedAtTimestamp = now.getTime();
        reportData.exportedAt = dateTimeFormat.format(now);
        reportData.exportedBy = options.getExportedBy();
        reportData.baseUrl = options.getBaseUrl();
        reportData.options = options;

        TagDao tagDao = new TagDao();
        List<TagDto> visibleTags = tagDao.findByCriteria(
                new TagCriteria().setTargetIdList(options.getTargetIdList()), null);

        if (visibleTags.isEmpty()) {
            return reportData;
        }

        Map<String, TagDto> visibleById = new LinkedHashMap<>();
        for (TagDto tag : visibleTags) {
            visibleById.put(tag.getId(), tag);
        }

        Set<String> selectedSet = new LinkedHashSet<>(options.getSelectedTagIds());
        List<TagDto> matchedTags = new ArrayList<>();
        for (TagDto tag : visibleTags) {
            if (selectedSet.isEmpty()) {
                if (!tag.isContainer()) {
                    matchedTags.add(tag);
                }
            } else if (selectedSet.contains(tag.getId())) {
                if (!tag.isContainer()) {
                    matchedTags.add(tag);
                }
            } else if (options.isIncludeChildren() && !tag.isContainer() && hasSelectedAncestor(tag, visibleById, selectedSet)) {
                matchedTags.add(tag);
            }
        }

        if (matchedTags.isEmpty()) {
            return reportData;
        }

        Map<String, ReportTagNode> nodeById = new LinkedHashMap<>();
        int rootCounter = 0;
        for (TagDto tag : matchedTags) {
            ReportTagNode node = new ReportTagNode();
            node.id = tag.getId();
            node.parentId = tag.getParentId();
            node.order = tag.getOrder();
            node.name = tag.getName();
            node.color = !Strings.isNullOrEmpty(tag.getColor()) ? tag.getColor() : "#3a87ad";
            node.creator = tag.getCreator();
            node.createTimestamp = tag.getCreateDate() != null ? tag.getCreateDate().getTime() : null;
            node.container = tag.isContainer();

            ReportTagNode parentNode = findNearestIncludedAncestor(tag, visibleById, nodeById);
            if (parentNode == null) {
                rootCounter++;
                node.depth = 0;
                node.hierarchyCode = String.valueOf(rootCounter);
                reportData.rootTags.add(node);
            } else {
                parentNode.childCounter++;
                node.depth = parentNode.depth + 1;
                node.hierarchyCode = parentNode.hierarchyCode + "." + parentNode.childCounter;
                parentNode.children.add(node);
            }

            nodeById.put(node.id, node);
            reportData.flatTags.add(node);
        }

        EntityManager em = ThreadLocalContext.get().getEntityManager();
        AclDao aclDao = new AclDao();
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();

        Set<String> uniqueDocIds = new HashSet<>();
        Set<String> uniqueFileIds = new HashSet<>();
        long uniqueTotalSize = 0L;
        int totalTagDocLinks = 0;

        for (ReportTagNode tagNode : reportData.flatTags) {
            if (tagNode.container) {
                continue;
            }

            Query q = em.createNativeQuery(
                    "select dt.DOT_IDDOCUMENT_C, dt.DOT_ORDER_N " +
                    "from T_DOCUMENT_TAG dt " +
                    "join T_DOCUMENT d on d.DOC_ID_C = dt.DOT_IDDOCUMENT_C and d.DOC_DELETEDATE_D is null " +
                    "where dt.DOT_IDTAG_C = :tagId and dt.DOT_DELETEDATE_D is null " +
                    "order by coalesce(dt.DOT_ORDER_N, 999999999) asc, d.DOC_CREATEDATE_D asc, d.DOC_TITLE_C asc");
            q.setParameter("tagId", tagNode.id);
            List<Object[]> rows = q.getResultList();

            long tagSizeSum = 0L;
            int tagFileSum = 0;

            for (Object[] row : rows) {
                String docId = (String) row[0];
                Number dotOrder = (Number) row[1];

                if (!aclDao.checkPermission(docId, PermType.READ, options.getTargetIdList())) {
                    continue;
                }

                DocumentDto docDto = documentDao.getDocument(docId, PermType.READ, options.getTargetIdList());
                if (docDto == null) {
                    continue;
                }
                Document docEntity = documentDao.getById(docId);

                ReportDocumentNode docNode = new ReportDocumentNode();
                docNode.id = docDto.getId();
                docNode.order = dotOrder != null ? dotOrder.intValue() : null;
                docNode.fileId = docDto.getFileId();
                docNode.title = docDto.getTitle();
                docNode.description = docDto.getDescription();
                docNode.plainDescription = stripHtml(docDto.getDescription());
                docNode.subject = docDto.getSubject();
                docNode.identifier = docDto.getIdentifier();
                docNode.publisher = docDto.getPublisher();
                docNode.format = docDto.getFormat();
                docNode.source = docDto.getSource();
                docNode.type = docDto.getType();
                docNode.coverage = docDto.getCoverage();
                docNode.rights = docDto.getRights();
                docNode.language = docDto.getLanguage();
                docNode.creator = docDto.getCreator();
                docNode.userId = docDto.getUserId();
                docNode.shared = Boolean.TRUE.equals(docDto.getShared());
                docNode.allowDownload = docEntity != null && docEntity.isAllowDownload();
                docNode.canDownload = ConfigUtil.canFileDownload()
                        || options.isAdminOrPrivileged()
                        || (options.getUserId() != null && options.getUserId().equals(docDto.getUserId()))
                        || docNode.allowDownload;
                docNode.createTimestamp = docDto.getCreateTimestamp();
                docNode.updateTimestamp = docDto.getUpdateTimestamp();
                docNode.formattedCreateDate = docDto.getCreateTimestamp() != null
                        ? dateFormat.format(new Date(docDto.getCreateTimestamp())) : "";
                docNode.formattedUpdateDate = docDto.getUpdateTimestamp() != null
                        ? dateFormat.format(new Date(docDto.getUpdateTimestamp())) : "";

                // Custom metadata
                Query metaQuery = em.createNativeQuery(
                        "select m.MET_ID_C, m.MET_NAME_C, m.MET_TYPE_C, dm.DME_VALUE_C " +
                        "from T_DOCUMENT_METADATA dm " +
                        "join T_METADATA m on dm.DME_IDMETADATA_C = m.MET_ID_C " +
                        "where dm.DME_IDDOCUMENT_C = :documentId and m.MET_DELETEDATE_D is null " +
                        "order by m.MET_NAME_C asc");
                metaQuery.setParameter("documentId", docId);
                List<Object[]> metaRows = metaQuery.getResultList();
                for (Object[] mRow : metaRows) {
                    ReportMetadataNode metaNode = new ReportMetadataNode();
                    metaNode.id = (String) mRow[0];
                    metaNode.name = (String) mRow[1];
                    metaNode.type = (String) mRow[2];
                    metaNode.value = (String) mRow[3];
                    docNode.metadata.add(metaNode);
                }

                // Active latest files ordered by FIL_ORDER_N ASC
                List<File> files = new ArrayList<>(fileDao.getByDocumentId(null, docId));
                files.sort(Comparator
                        .comparing((File f) -> f.getOrder() != null ? f.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(f -> f.getCreateDate() != null ? f.getCreateDate().getTime() : 0L));

                for (File f : files) {
                    ReportFileNode fileNode = new ReportFileNode();
                    fileNode.id = f.getId();
                    fileNode.order = f.getOrder();
                    fileNode.name = resolveFileName(f, docDto.getTitle());
                    fileNode.mimeType = f.getMimeType();
                    fileNode.size = resolveFileSize(f);
                    fileNode.formattedSize = formatFileSize(fileNode.size);
                    fileNode.version = f.getVersion();
                    fileNode.versionLabel = f.getVersionLabel();
                    fileNode.versionDisplay = !Strings.isNullOrEmpty(f.getVersionLabel())
                            ? f.getVersionLabel()
                            : "v" + ((f.getVersion() != null ? f.getVersion() : 0) + 1) + ".0";
                    fileNode.versionId = f.getVersionId();
                    fileNode.createTimestamp = f.getCreateDate() != null ? f.getCreateDate().getTime() : null;
                    fileNode.formattedCreateDate = f.getCreateDate() != null ? dateFormat.format(f.getCreateDate()) : "";
                    fileNode.previewUrl = options.getBaseUrl() + "/#/document/" + docId + "/file/" + f.getId();
                    fileNode.downloadUrl = options.getBaseUrl() + "/api/file/" + f.getId() + "/data";
                    if ("download".equalsIgnoreCase(options.getLinkMode()) && docNode.canDownload) {
                        fileNode.effectiveLinkMode = "download";
                        fileNode.effectiveUrl = fileNode.downloadUrl;
                    } else {
                        fileNode.effectiveLinkMode = "preview";
                        fileNode.effectiveUrl = fileNode.previewUrl;
                    }

                    docNode.files.add(fileNode);
                    tagFileSum++;
                    tagSizeSum += fileNode.size;
                    if (uniqueFileIds.add(fileNode.id)) {
                        uniqueTotalSize += fileNode.size;
                    }
                }

                tagNode.documents.add(docNode);
                totalTagDocLinks++;
                uniqueDocIds.add(docId);
            }

            tagNode.documentCount = tagNode.documents.size();
            tagNode.fileCount = tagFileSum;
            tagNode.totalSize = tagSizeSum;
            tagNode.formattedTotalSize = formatFileSize(tagSizeSum);
        }

        reportData.statistics.totalTags = reportData.flatTags.size();
        reportData.statistics.totalTagDocumentLinks = totalTagDocLinks;
        reportData.statistics.uniqueDocuments = uniqueDocIds.size();
        reportData.statistics.uniqueFiles = uniqueFileIds.size();
        reportData.statistics.totalSize = uniqueTotalSize;
        reportData.statistics.formattedTotalSize = formatFileSize(uniqueTotalSize);

        return reportData;
    }

    /**
     * Exports report data as complete, raw JSON (always includes dates, descriptions, metadata, and both URLs).
     */
    public void exportJson(ReportData data, OutputStream out) throws Exception {
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        JsonObjectBuilder root = Json.createObjectBuilder()
                .add("exported_at", data.getExportedAtTimestamp())
                .add("exported_at_iso", isoFormat.format(new Date(data.getExportedAtTimestamp())))
                .add("exported_by", data.getExportedBy())
                .add("base_url", data.getBaseUrl())
                .add("options", Json.createObjectBuilder()
                        .add("include_children", data.getOptions().isIncludeChildren())
                        .add("include_date", data.getOptions().isIncludeDate())
                        .add("include_description", data.getOptions().isIncludeDescription())
                        .add("include_size", data.getOptions().isIncludeSize())
                        .add("link_mode", data.getOptions().getLinkMode()))
                .add("statistics", Json.createObjectBuilder()
                        .add("total_tags", data.getStatistics().getTotalTags())
                        .add("total_tag_document_links", data.getStatistics().getTotalTagDocumentLinks())
                        .add("unique_documents", data.getStatistics().getUniqueDocuments())
                        .add("unique_files", data.getStatistics().getUniqueFiles())
                        .add("total_size", data.getStatistics().getTotalSize()));

        JsonArrayBuilder tagsArray = Json.createArrayBuilder();
        for (ReportTagNode rootTag : data.getRootTags()) {
            tagsArray.add(buildTagJson(rootTag));
        }
        root.add("tags", tagsArray);

        JsonArrayBuilder flatTagsArray = Json.createArrayBuilder();
        for (ReportTagNode tag : data.getFlatTags()) {
            flatTagsArray.add(buildTagJson(tag));
        }
        root.add("flat_tags", flatTagsArray);

        Map<String, Object> config = Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
        JsonWriterFactory writerFactory = Json.createWriterFactory(config);
        Writer streamWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        try (JsonWriter jsonWriter = writerFactory.createWriter(streamWriter)) {
            jsonWriter.writeObject(root.build());
        }
    }

    /**
     * Exports report data as a ZIP archive containing the raw JSON file and an English README.md
     * explaining the JSON schema and field semantics for downstream tools and AI agents.
     */
    public void exportJsonZip(ReportData data, OutputStream out, String jsonFileName) throws Exception {
        String effectiveJsonName = Strings.isNullOrEmpty(jsonFileName) ? "tag-report.json" : jsonFileName;
        ByteArrayOutputStream jsonBuffer = new ByteArrayOutputStream();
        exportJson(data, jsonBuffer);

        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            ZipEntry jsonEntry = new ZipEntry(effectiveJsonName);
            zos.putNextEntry(jsonEntry);
            zos.write(jsonBuffer.toByteArray());
            zos.closeEntry();

            ZipEntry readmeEntry = new ZipEntry("README.md");
            zos.putNextEntry(readmeEntry);
            zos.write(buildJsonReadme(effectiveJsonName).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
        }
    }

    private String buildJsonReadme(String jsonFileName) {
        return "# Tag Report JSON Export Schema\n\n"
                + "This archive was exported from **FormosaBearV Docs** and contains raw hierarchical tag, document, metadata, and file data.\n\n"
                + "## Archive Contents\n\n"
                + "- `" + jsonFileName + "`: Complete machine-readable JSON report (UTF-8, pretty-printed).\n"
                + "- `README.md`: This schema reference documenting every field for scripts, report generators, and AI assistants.\n\n"
                + "## Important Notes for Parsers & AI\n\n"
                + "1. **Full Raw Data**: Unlike PDF, HTML, and Excel exports, the JSON export **always** includes all raw fields (dates, descriptions, metadata, file sizes, MIME types, creators, languages, and both preview/download URLs), regardless of `options.include_date`, `options.include_description`, or `options.include_size`.\n"
                + "2. **Timestamps**: All `*_date` and `exported_at` numeric fields are **Unix epoch milliseconds (UTC)** (`long`, or `null` if unset). `exported_at_iso` provides an ISO-8601 UTC string.\n"
                + "3. **Tree vs. Flat Representation**:\n"
                + "   - `tags` is a nested tree of root-level tags (each containing recursive `children` tags).\n"
                + "   - `flat_tags` is a pre-order flattened list of all tags in the exact display order. Note that each element in `flat_tags` still uses the same tag object structure (including `children`).\n"
                + "4. **Hierarchy & Ordering**:\n"
                + "   - `hierarchy_code` is a dot-separated positional code (e.g., `\"1\"`, `\"1.1\"`, `\"1.2\"`) representing the tag's position in the exported tree.\n"
                + "   - Tags, documents, and files are pre-sorted by their configured display `order` (with creation date / title as tie-breakers).\n"
                + "5. **Shared Documents Across Multiple Tags**: A single document can be attached to multiple tags. Therefore, the same document ID may appear under multiple tags in `tags` / `flat_tags`. Use `statistics.unique_documents` and `statistics.unique_files` for deduplicated totals.\n\n"
                + "---\n\n"
                + "## Top-Level Object\n\n"
                + "| Field | Type | Description |\n"
                + "| --- | --- | --- |\n"
                + "| `exported_at` | `number` (int64) | Export timestamp in Unix epoch milliseconds (UTC). |\n"
                + "| `exported_at_iso` | `string` | Export timestamp in ISO-8601 UTC format (`yyyy-MM-dd'T'HH:mm:ss'Z'`). |\n"
                + "| `exported_by` | `string` | Username of the user who triggered the export. |\n"
                + "| `base_url` | `string` | Base URL of the Docs server used to construct absolute file URLs. |\n"
                + "| `options` | `object` | Export parameters passed to the API endpoint (see below). |\n"
                + "| `statistics` | `object` | Global summary counts across the exported scope (see below). |\n"
                + "| `tags` | `array<Tag>` | Hierarchical tree of selected root tags and their descendants. |\n"
                + "| `flat_tags` | `array<Tag>` | Pre-order flattened list of all exported tags. |\n\n"
                + "### `options` Object\n\n"
                + "| Field | Type | Description |\n"
                + "| --- | --- | --- |\n"
                + "| `include_children` | `boolean` | Whether descendant child tags of selected tags were included. |\n"
                + "| `include_date` | `boolean` | UI/formatted-report preference for showing dates (does not omit dates from JSON). |\n"
                + "| `include_description` | `boolean` | UI/formatted-report preference for showing descriptions (does not omit descriptions from JSON). |\n"
                + "| `include_size` | `boolean` | UI/formatted-report preference for showing file sizes (does not omit sizes from JSON). |\n"
                + "| `link_mode` | `string` | Requested file link mode: `\"preview\"` or `\"download\"`. |\n\n"
                + "### `statistics` Object\n\n"
                + "| Field | Type | Description |\n"
                + "| --- | --- | --- |\n"
                + "| `total_tags` | `number` | Total number of tags included in the export. |\n"
                + "| `total_tag_document_links` | `number` | Total tag-to-document associations (counts a multi-tagged document once per tag). |\n"
                + "| `unique_documents` | `number` | Count of distinct documents across all exported tags. |\n"
                + "| `unique_files` | `number` | Count of distinct latest-version files across all exported documents. |\n"
                + "| `total_size` | `number` (int64) | Deduplicated total file size in bytes across `unique_files`. |\n\n"
                + "---\n\n"
                + "## `Tag` Object\n\n"
                + "| Field | Type | Description |\n"
                + "| --- | --- | --- |\n"
                + "| `id` | `string` | Unique tag UUID. |\n"
                + "| `parent_id` | `string \\| null` | Parent tag UUID, or `null` if top-level / root in this export. |\n"
                + "| `hierarchy_code` | `string` | Positional hierarchy index (e.g., `\"1\"`, `\"1.1\"`, `\"2.1.3\"`). |\n"
                + "| `depth` | `number` | Zero-based nesting depth (`0` for root tags in the export). |\n"
                + "| `order` | `number \\| null` | Configured display sort order of the tag. |\n"
                + "| `name` | `string \\| null` | Tag display name. |\n"
                + "| `color` | `string \\| null` | Tag hex color code (e.g., `#3a87ad`). |\n"
                + "| `creator` | `string \\| null` | Username of the tag creator. |\n"
                + "| `create_date` | `number \\| null` | Tag creation timestamp (Unix epoch ms, UTC). |\n"
                + "| `document_count` | `number` | Number of documents directly attached to this tag. |\n"
                + "| `file_count` | `number` | Total number of files belonging to documents directly attached to this tag. |\n"
                + "| `total_size` | `number` (int64) | Total byte size of files directly under this tag's documents. |\n"
                + "| `documents` | `array<Document>` | Documents directly attached to this tag (sorted). |\n"
                + "| `children` | `array<Tag>` | Direct child tags (recursive `Tag` objects, sorted). |\n\n"
                + "---\n\n"
                + "## `Document` Object\n\n"
                + "| Field | Type | Description |\n"
                + "| --- | --- | --- |\n"
                + "| `id` | `string` | Unique document UUID. |\n"
                + "| `order` | `number` | Display order within this tag (1-based fallback if no explicit per-tag order is set). |\n"
                + "| `file_id` | `string \\| null` | Primary/thumbnail file UUID of the document, if any. |\n"
                + "| `title` | `string \\| null` | Document title. |\n"
                + "| `description` | `string \\| null` | Document description (may contain HTML markup). |\n"
                + "| `subject` | `string \\| null` | Dublin Core Subject field. |\n"
                + "| `identifier` | `string \\| null` | Dublin Core Identifier field. |\n"
                + "| `publisher` | `string \\| null` | Dublin Core Publisher field. |\n"
                + "| `format` | `string \\| null` | Dublin Core Format field. |\n"
                + "| `source` | `string \\| null` | Dublin Core Source field. |\n"
                + "| `type` | `string \\| null` | Dublin Core Type field. |\n"
                + "| `coverage` | `string \\| null` | Dublin Core Coverage field. |\n"
                + "| `rights` | `string \\| null` | Dublin Core Rights field. |\n"
                + "| `language` | `string \\| null` | ISO language code of the document (e.g., `\"eng\"`, `\"chi_tra\"`). |\n"
                + "| `creator` | `string \\| null` | Username of the document creator. |\n"
                + "| `user_id` | `string \\| null` | Internal user UUID of the document creator. |\n"
                + "| `shared` | `boolean` | Whether the document has active public share links. |\n"
                + "| `allow_download` | `boolean` | Document-level download permission flag. |\n"
                + "| `can_download` | `boolean` | Effective download permission for the exporting user (considers global config, admin role, ownership, and `allow_download`). |\n"
                + "| `create_date` | `number \\| null` | Document creation timestamp (Unix epoch ms, UTC). |\n"
                + "| `update_date` | `number \\| null` | Document last-update timestamp (Unix epoch ms, UTC). |\n"
                + "| `metadata` | `array<Metadata>` | Custom metadata key-value entries attached to the document. |\n"
                + "| `files` | `array<File>` | Latest-version files attached to the document (sorted). |\n\n"
                + "---\n\n"
                + "## `Metadata` Object\n\n"
                + "| Field | Type | Description |\n"
                + "| --- | --- | --- |\n"
                + "| `id` | `string` | Metadata definition UUID. |\n"
                + "| `name` | `string \\| null` | Custom metadata field name. |\n"
                + "| `type` | `string \\| null` | Metadata data type (e.g., `STRING`, `INTEGER`, `FLOAT`, `DATE`, `BOOLEAN`). |\n"
                + "| `value` | `string \\| null` | Stored metadata value as a string. |\n\n"
                + "---\n\n"
                + "## `File` Object\n\n"
                + "| Field | Type | Description |\n"
                + "| --- | --- | --- |\n"
                + "| `id` | `string` | Unique file UUID. |\n"
                + "| `order` | `number` | Display order of the file within the document. |\n"
                + "| `name` | `string \\| null` | Original filename (with extension). |\n"
                + "| `mime_type` | `string \\| null` | Detected MIME type (e.g., `\"application/pdf\"`). |\n"
                + "| `size` | `number` (int64) | File size in bytes (`0` if unknown). |\n"
                + "| `version` | `number \\| null` | Numeric file version counter. |\n"
                + "| `version_label` | `string \\| null` | Human-readable version label, if set. |\n"
                + "| `version_id` | `string \\| null` | Shared version-group UUID linking revisions of the same file. |\n"
                + "| `create_date` | `number \\| null` | File upload/creation timestamp (Unix epoch ms, UTC). |\n"
                + "| `preview_url` | `string` | Direct web UI URL to preview this file (`{base_url}/#/document/{doc_id}/file/{file_id}`). |\n"
                + "| `download_url` | `string` | Direct API URL to download this file's binary data (`{base_url}/api/file/{file_id}/data`). |\n"
                + "| `effective_link_mode` | `string` | Resolved link mode for the exporting user (`\"preview\"` or `\"download\"`; falls back to `\"preview\"` if `can_download` is `false`). |\n"
                + "| `effective_url` | `string` | Resolved URL matching `effective_link_mode` (`preview_url` or `download_url`). |\n";
    }

    private JsonObjectBuilder buildTagJson(ReportTagNode tag) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("id", tag.id)
                .add("parent_id", tag.parentId != null ? Json.createValue(tag.parentId) : JsonValue.NULL)
                .add("hierarchy_code", tag.hierarchyCode)
                .add("depth", tag.depth)
                .add("order", tag.order != null ? Json.createValue(tag.order) : JsonValue.NULL)
                .add("name", tag.name != null ? Json.createValue(tag.name) : JsonValue.NULL)
                .add("color", tag.color != null ? Json.createValue(tag.color) : JsonValue.NULL)
                .add("creator", tag.creator != null ? Json.createValue(tag.creator) : JsonValue.NULL)
                .add("create_date", tag.createTimestamp != null ? Json.createValue(tag.createTimestamp) : JsonValue.NULL)
                .add("document_count", tag.documentCount)
                .add("file_count", tag.fileCount)
                .add("total_size", tag.totalSize);

        JsonArrayBuilder docsArray = Json.createArrayBuilder();
        int docPosition = 0;
        for (ReportDocumentNode doc : tag.documents) {
            docPosition++;
            JsonObjectBuilder docBuilder = Json.createObjectBuilder()
                    .add("id", doc.id)
                    .add("order", doc.order != null ? Json.createValue(doc.order) : Json.createValue(docPosition))
                    .add("file_id", doc.fileId != null ? Json.createValue(doc.fileId) : JsonValue.NULL)
                    .add("title", doc.title != null ? Json.createValue(doc.title) : JsonValue.NULL)
                    .add("description", doc.description != null ? Json.createValue(doc.description) : JsonValue.NULL)
                    .add("subject", doc.subject != null ? Json.createValue(doc.subject) : JsonValue.NULL)
                    .add("identifier", doc.identifier != null ? Json.createValue(doc.identifier) : JsonValue.NULL)
                    .add("publisher", doc.publisher != null ? Json.createValue(doc.publisher) : JsonValue.NULL)
                    .add("format", doc.format != null ? Json.createValue(doc.format) : JsonValue.NULL)
                    .add("source", doc.source != null ? Json.createValue(doc.source) : JsonValue.NULL)
                    .add("type", doc.type != null ? Json.createValue(doc.type) : JsonValue.NULL)
                    .add("coverage", doc.coverage != null ? Json.createValue(doc.coverage) : JsonValue.NULL)
                    .add("rights", doc.rights != null ? Json.createValue(doc.rights) : JsonValue.NULL)
                    .add("language", doc.language != null ? Json.createValue(doc.language) : JsonValue.NULL)
                    .add("creator", doc.creator != null ? Json.createValue(doc.creator) : JsonValue.NULL)
                    .add("user_id", doc.userId != null ? Json.createValue(doc.userId) : JsonValue.NULL)
                    .add("shared", doc.shared)
                    .add("allow_download", doc.allowDownload)
                    .add("can_download", doc.canDownload)
                    .add("create_date", doc.createTimestamp != null ? Json.createValue(doc.createTimestamp) : JsonValue.NULL)
                    .add("update_date", doc.updateTimestamp != null ? Json.createValue(doc.updateTimestamp) : JsonValue.NULL);

            JsonArrayBuilder metaArray = Json.createArrayBuilder();
            for (ReportMetadataNode meta : doc.metadata) {
                metaArray.add(Json.createObjectBuilder()
                        .add("id", meta.id)
                        .add("name", meta.name != null ? Json.createValue(meta.name) : JsonValue.NULL)
                        .add("type", meta.type != null ? Json.createValue(meta.type) : JsonValue.NULL)
                        .add("value", meta.value != null ? Json.createValue(meta.value) : JsonValue.NULL));
            }
            docBuilder.add("metadata", metaArray);

            JsonArrayBuilder filesArray = Json.createArrayBuilder();
            int filePosition = 0;
            for (ReportFileNode file : doc.files) {
                filePosition++;
                filesArray.add(Json.createObjectBuilder()
                        .add("id", file.id)
                        .add("order", file.order != null ? Json.createValue(file.order) : Json.createValue(filePosition))
                        .add("name", file.name != null ? Json.createValue(file.name) : JsonValue.NULL)
                        .add("mime_type", file.mimeType != null ? Json.createValue(file.mimeType) : JsonValue.NULL)
                        .add("size", file.size)
                        .add("version", file.version != null ? Json.createValue(file.version) : JsonValue.NULL)
                        .add("version_label", file.versionLabel != null ? Json.createValue(file.versionLabel) : JsonValue.NULL)
                        .add("version_id", file.versionId != null ? Json.createValue(file.versionId) : JsonValue.NULL)
                        .add("create_date", file.createTimestamp != null ? Json.createValue(file.createTimestamp) : JsonValue.NULL)
                        .add("preview_url", file.previewUrl)
                        .add("download_url", file.downloadUrl)
                        .add("effective_link_mode", file.effectiveLinkMode)
                        .add("effective_url", file.effectiveUrl));
            }
            docBuilder.add("files", filesArray);
            docsArray.add(docBuilder);
        }
        b.add("documents", docsArray);

        JsonArrayBuilder childrenArray = Json.createArrayBuilder();
        for (ReportTagNode child : tag.children) {
            childrenArray.add(buildTagJson(child));
        }
        b.add("children", childrenArray);
        return b;
    }

    /**
     * Exports report data as a 2-sheet Excel (.xlsx) workbook (`統計總覽` & `標籤文件明細`).
     */
    public void exportExcel(ReportData data, OutputStream out) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // Fonts
            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFFont headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFFont rootTagFont = wb.createFont();
            rootTagFont.setBold(true);
            rootTagFont.setFontHeightInPoints((short) 11);
            rootTagFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFFont childTagFont = wb.createFont();
            childTagFont.setBold(true);
            childTagFont.setFontHeightInPoints((short) 11);
            childTagFont.setColor(new XSSFColor(new byte[]{(byte) 30, (byte) 58, (byte) 95}, null));

            XSSFFont docFont = wb.createFont();
            docFont.setBold(true);
            docFont.setFontHeightInPoints((short) 10);

            XSSFFont normalFont = wb.createFont();
            normalFont.setFontHeightInPoints((short) 10);

            XSSFFont linkFont = wb.createFont();
            linkFont.setFontHeightInPoints((short) 10);
            linkFont.setUnderline(XSSFFont.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());

            // Styles
            XSSFCellStyle bannerStyle = wb.createCellStyle();
            bannerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 58, (byte) 95}, null));
            bannerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bannerStyle.setFont(titleFont);
            bannerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle tableHeaderStyle = wb.createCellStyle();
            tableHeaderStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 51, (byte) 65, (byte) 85}, null));
            tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tableHeaderStyle.setFont(headerFont);
            tableHeaderStyle.setAlignment(HorizontalAlignment.LEFT);
            tableHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorder(tableHeaderStyle);

            XSSFCellStyle rootTagStyle = wb.createCellStyle();
            rootTagStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 58, (byte) 95}, null));
            rootTagStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            rootTagStyle.setFont(rootTagFont);
            rootTagStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorder(rootTagStyle);

            XSSFCellStyle childTagStyle = wb.createCellStyle();
            childTagStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 232, (byte) 241, (byte) 250}, null));
            childTagStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            childTagStyle.setFont(childTagFont);
            childTagStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorder(childTagStyle);

            XSSFCellStyle docRowStyle = wb.createCellStyle();
            docRowStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 241, (byte) 245, (byte) 249}, null));
            docRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            docRowStyle.setFont(docFont);
            docRowStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            docRowStyle.setWrapText(true);
            setThinBorder(docRowStyle);

            XSSFCellStyle fileRowStyle = wb.createCellStyle();
            fileRowStyle.setFont(normalFont);
            fileRowStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorder(fileRowStyle);

            XSSFCellStyle linkCellStyle = wb.createCellStyle();
            linkCellStyle.setFont(linkFont);
            linkCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorder(linkCellStyle);

            // =========================================================================
            // Sheet 1: Summary (統計總覽)
            // =========================================================================
            Map<String, String> labels = getReportLabels(data.getOptions().getLang());
            XSSFSheet summarySheet = wb.createSheet(labels.get("sheetSummary"));
            int sRowIdx = 0;

            Row metaRow = summarySheet.createRow(sRowIdx++);
            metaRow.createCell(0).setCellValue(labels.get("exportedAtPrefix") + data.getExportedAt());
            metaRow.createCell(2).setCellValue(labels.get("excelLinkModePrefix") + ("download".equals(data.getOptions().getLinkMode()) ? labels.get("downloadFile") : labels.get("previewFile")));
            sRowIdx++;

            // Global KPI Table
            Row kpiHeader = summarySheet.createRow(sRowIdx++);
            String[] kpiTitles = new String[]{
                    labels.get("kpiSelectedTags"),
                    labels.get("kpiTagDocLinks"),
                    labels.get("kpiUniqueDocs"),
                    labels.get("kpiUniqueFiles")
            };
            for (int i = 0; i < kpiTitles.length; i++) {
                Cell c = kpiHeader.createCell(i);
                c.setCellStyle(tableHeaderStyle);
                c.setCellValue(kpiTitles[i]);
            }
            Row kpiValues = summarySheet.createRow(sRowIdx++);
            createStyledCell(kpiValues, 0, data.getStatistics().getTotalTags(), fileRowStyle);
            createStyledCell(kpiValues, 1, data.getStatistics().getTotalTagDocumentLinks(), fileRowStyle);
            createStyledCell(kpiValues, 2, data.getStatistics().getUniqueDocuments(), fileRowStyle);
            createStyledCell(kpiValues, 3, data.getStatistics().getUniqueFiles(), fileRowStyle);
            sRowIdx++;

            // Per-Tag Breakdown Table
            Row tagSummaryHeader = summarySheet.createRow(sRowIdx++);
            String[] tagSummaryCols = new String[]{
                    labels.get("colHierarchyCode"),
                    labels.get("colTagName"),
                    labels.get("colDirectDocCount"),
                    labels.get("colDirectFileCount")
            };
            for (int i = 0; i < tagSummaryCols.length; i++) {
                Cell c = tagSummaryHeader.createCell(i);
                c.setCellStyle(tableHeaderStyle);
                c.setCellValue(tagSummaryCols[i]);
            }
            for (ReportTagNode tag : data.getFlatTags()) {
                Row r = summarySheet.createRow(sRowIdx++);
                XSSFCellStyle st = tag.getDepth() == 0 ? docRowStyle : fileRowStyle;
                createStyledCell(r, 0, tag.getHierarchyCode(), st);
                createStyledCell(r, 1, tag.getName(), st);
                createStyledCell(r, 2, tag.getDocumentCount(), st);
                createStyledCell(r, 3, tag.getFileCount(), st);
            }
            for (int i = 0; i < 4; i++) {
                summarySheet.setColumnWidth(i, 22 * 256);
            }

            // =========================================================================
            // Sheet 2: Tag Document Details (標籤文件明細 - Fully expanded, no outline folding)
            // =========================================================================
            XSSFSheet detailSheet = wb.createSheet(labels.get("sheetDetails"));
            List<String> detailHeaders = new ArrayList<>();
            detailHeaders.add(labels.get("colItemType"));
            detailHeaders.add(labels.get("colHierarchyOrder"));
            detailHeaders.add(labels.get("colEntityName"));
            detailHeaders.add(labels.get("colVersion"));
            detailHeaders.add(labels.get("colCountSummary"));
            if (data.getOptions().isIncludeSize()) {
                detailHeaders.add(labels.get("colFileSize"));
            }
            if (data.getOptions().isIncludeDate()) {
                detailHeaders.add(labels.get("colCreateUpdateDate"));
            }
            if (data.getOptions().isIncludeDescription()) {
                detailHeaders.add(labels.get("colDescription"));
            }
            detailHeaders.add(labels.get("colFileLink"));

            int totalCols = detailHeaders.size();
            int linkColIdx = totalCols - 1;

            int dRowIdx = 0;
            Row headerRow = detailSheet.createRow(dRowIdx++);
            headerRow.setHeightInPoints(24f);
            for (int i = 0; i < totalCols; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellStyle(tableHeaderStyle);
                cell.setCellValue(detailHeaders.get(i));
            }

            for (ReportTagNode tag : data.getFlatTags()) {
                Row tagRow = detailSheet.createRow(dRowIdx++);
                tagRow.setHeightInPoints(22f);
                XSSFCellStyle tagStyle = tag.getDepth() == 0 ? rootTagStyle : childTagStyle;
                int col = 0;
                createStyledCell(tagRow, col++, tag.getDepth() == 0 ? labels.get("typeRootTag") : labels.get("typeChildTag"), tagStyle);
                createStyledCell(tagRow, col++, tag.getHierarchyCode(), tagStyle);
                createStyledCell(tagRow, col++, tag.getName(), tagStyle);
                createStyledCell(tagRow, col++, "", tagStyle);
                createStyledCell(tagRow, col++, labels.get("excelTagDocPrefix") + tag.getDocumentCount() + labels.get("excelTagFilePrefix") + tag.getFileCount(), tagStyle);
                if (data.getOptions().isIncludeSize()) {
                    createStyledCell(tagRow, col++, "", tagStyle);
                }
                if (data.getOptions().isIncludeDate()) {
                    createStyledCell(tagRow, col++, "", tagStyle);
                }
                if (data.getOptions().isIncludeDescription()) {
                    createStyledCell(tagRow, col++, "", tagStyle);
                }
                createStyledCell(tagRow, col, "", tagStyle);

                int docIndex = 0;
                for (ReportDocumentNode doc : tag.getDocuments()) {
                    docIndex++;
                    Row docRow = detailSheet.createRow(dRowIdx++);
                    int dCol = 0;
                    createStyledCell(docRow, dCol++, labels.get("typeDocument"), docRowStyle);
                    createStyledCell(docRow, dCol++, tag.getHierarchyCode() + " - #" + docIndex, docRowStyle);
                    createStyledCell(docRow, dCol++, doc.getTitle() != null ? doc.getTitle() : labels.get("untitledDocument"), docRowStyle);
                    createStyledCell(docRow, dCol++, "", docRowStyle);
                    createStyledCell(docRow, dCol++, labels.get("excelDocFilesPrefix") + doc.getFiles().size(), docRowStyle);
                    if (data.getOptions().isIncludeSize()) {
                        createStyledCell(docRow, dCol++, "", docRowStyle);
                    }
                    if (data.getOptions().isIncludeDate()) {
                        String dateText = doc.getFormattedCreateDate();
                        if (!Strings.isNullOrEmpty(doc.getFormattedUpdateDate())) {
                            dateText = dateText + labels.get("excelUpdatedPrefix") + doc.getFormattedUpdateDate() + ")";
                        }
                        createStyledCell(docRow, dCol++, dateText, docRowStyle);
                    }
                    if (data.getOptions().isIncludeDescription()) {
                        createStyledCell(docRow, dCol++, doc.getPlainDescription() != null ? doc.getPlainDescription() : "", docRowStyle);
                    }
                    createStyledCell(docRow, dCol, "", docRowStyle);

                    int fileIndex = 0;
                    for (ReportFileNode file : doc.getFiles()) {
                        fileIndex++;
                        Row fileRow = detailSheet.createRow(dRowIdx++);
                        int fCol = 0;
                        createStyledCell(fileRow, fCol++, labels.get("typeFile"), fileRowStyle);
                        createStyledCell(fileRow, fCol++, tag.getHierarchyCode() + " - #" + docIndex + "." + fileIndex, fileRowStyle);
                        createStyledCell(fileRow, fCol++, file.getName(), fileRowStyle);
                        createStyledCell(fileRow, fCol++, file.getVersionDisplay(), fileRowStyle);
                        createStyledCell(fileRow, fCol++, "", fileRowStyle);
                        if (data.getOptions().isIncludeSize()) {
                            createStyledCell(fileRow, fCol++, file.getFormattedSize(), fileRowStyle);
                        }
                        if (data.getOptions().isIncludeDate()) {
                            createStyledCell(fileRow, fCol++, file.getFormattedCreateDate(), fileRowStyle);
                        }
                        if (data.getOptions().isIncludeDescription()) {
                            createStyledCell(fileRow, fCol++, "", fileRowStyle);
                        }

                        Cell linkCell = fileRow.createCell(linkColIdx);
                        linkCell.setCellValue("download".equals(file.getEffectiveLinkMode()) ? labels.get("downloadFile") : labels.get("previewFile"));
                        XSSFHyperlink hyperlink = wb.getCreationHelper().createHyperlink(HyperlinkType.URL);
                        hyperlink.setAddress(file.getEffectiveUrl());
                        linkCell.setHyperlink(hyperlink);
                        linkCell.setCellStyle(linkCellStyle);
                    }
                }
            }

            detailSheet.createFreezePane(0, 1);
            if (dRowIdx > 1) {
                detailSheet.setAutoFilter(new CellRangeAddress(0, dRowIdx - 1, 0, totalCols - 1));
            }

            detailSheet.setColumnWidth(0, 14 * 256);
            detailSheet.setColumnWidth(1, 18 * 256);
            detailSheet.setColumnWidth(2, 38 * 256);
            detailSheet.setColumnWidth(3, 16 * 256);
            detailSheet.setColumnWidth(4, 24 * 256);
            detailSheet.setColumnWidth(5, 16 * 256);
            for (int c = 6; c < totalCols; c++) {
                detailSheet.setColumnWidth(c, 26 * 256);
            }

            wb.write(out);
        }
    }

    /**
     * Exports report data as a single-page fixed-width HTML report via FreeMarker.
     */
    public void exportHtml(ReportData data, OutputStream out) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setClassForTemplateLoading(TagReportService.class, "/");
        cfg.setDefaultEncoding("UTF-8");

        Template template = cfg.getTemplate("tag_report.ftl");
        Map<String, Object> model = new HashMap<>();
        model.put("exportedAt", data.getExportedAt());
        model.put("exportedBy", data.getExportedBy());
        model.put("baseUrl", data.getBaseUrl());
        model.put("options", data.getOptions());
        model.put("statistics", data.getStatistics());
        model.put("flatTags", data.getFlatTags());
        model.put("labels", getReportLabels(data.getOptions().getLang()));

        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        template.process(model, writer);
        writer.flush();
    }

    /**
     * Exports report data as an A4 PDF report with bundled Noto Sans CJK TC font, flush-left hierarchy codes, and clickable file links.
     */
    public void exportPdf(ReportData data, OutputStream out) throws Exception {
        PdfUtil.registerFonts();
        Map<String, String> labels = getReportLabels(data.getOptions().getLang());

        Font titleFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 18f, Font.BOLD, new Color(30, 58, 95));
        Font metaFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 9f, Font.NORMAL, new Color(71, 85, 105));
        Font kpiLabelFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 8f, Font.NORMAL, new Color(100, 116, 139));
        Font kpiValueFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 12f, Font.BOLD, new Color(15, 23, 42));
        Font rootTagFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 11f, Font.BOLD, Color.WHITE);
        Font childTagFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 11f, Font.BOLD, new Color(30, 58, 95));
        Font docTitleFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 10f, Font.BOLD, new Color(15, 23, 42));
        Font bodyFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 9f, Font.NORMAL, new Color(51, 65, 85));
        Font tableHeaderFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 9f, Font.BOLD, new Color(51, 65, 85));
        Font linkFont = FontFactory.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 9f, Font.UNDERLINE, new Color(37, 99, 235));

        com.lowagie.text.Document pdfDoc = new com.lowagie.text.Document(PageSize.A4, 32f, 32f, 32f, 32f);
        PdfWriter.getInstance(pdfDoc, out);
        pdfDoc.open();

        // Export metadata (no report title, no exportedBy)
        String modeLabel = "download".equals(data.getOptions().getLinkMode()) ? labels.get("downloadFile") : labels.get("previewFile");
        Paragraph metaPara = new Paragraph(
                labels.get("exportedAtPrefix") + data.getExportedAt() + "    " + labels.get("linkModePrefix") + modeLabel,
                metaFont);
        metaPara.setIndentationLeft(0f);
        metaPara.setSpacingAfter(12f);
        pdfDoc.add(metaPara);

        // Statistics summary bar (full width, flush left, without total size)
        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100f);
        kpiTable.setSpacingAfter(14f);
        addKpiCell(kpiTable, labels.get("kpiSelectedTags"), String.valueOf(data.getStatistics().getTotalTags()), kpiLabelFont, kpiValueFont);
        addKpiCell(kpiTable, labels.get("kpiTagDocLinks"), String.valueOf(data.getStatistics().getTotalTagDocumentLinks()), kpiLabelFont, kpiValueFont);
        addKpiCell(kpiTable, labels.get("kpiUniqueDocs"), String.valueOf(data.getStatistics().getUniqueDocuments()), kpiLabelFont, kpiValueFont);
        addKpiCell(kpiTable, labels.get("kpiUniqueFiles"), String.valueOf(data.getStatistics().getUniqueFiles()), kpiLabelFont, kpiValueFont);
        pdfDoc.add(kpiTable);

        // Flush-left tag sections (no indentation by depth)
        for (ReportTagNode tag : data.getFlatTags()) {
            Color tagColor = parseHexColor(tag.getColor(), new Color(58, 135, 173));
            Color bgColor = tag.getDepth() == 0 ? new Color(30, 58, 95) : new Color(232, 241, 250);
            Font tagFont = tag.getDepth() == 0 ? rootTagFont : childTagFont;

            PdfPTable tagBanner = new PdfPTable(new float[]{0.02f, 0.72f, 0.26f});
            tagBanner.setWidthPercentage(100f);
            tagBanner.setSpacingBefore(8f);
            tagBanner.setSpacingAfter(4f);

            // Left color bar cell
            PdfPCell colorBarCell = new PdfPCell(new Phrase(""));
            colorBarCell.setBackgroundColor(tagColor);
            colorBarCell.setBorderColor(new Color(203, 213, 225));
            colorBarCell.setPadding(0f);
            tagBanner.addCell(colorBarCell);

            // Tag hierarchy code + name cell (flush left)
            PdfPCell tagTitleCell = new PdfPCell(new Phrase("[" + tag.getHierarchyCode() + "] " + tag.getName(), tagFont));
            tagTitleCell.setBackgroundColor(bgColor);
            tagTitleCell.setBorderColor(new Color(203, 213, 225));
            tagTitleCell.setPadding(7f);
            tagBanner.addCell(tagTitleCell);

            // Tag stats cell (without total size)
            PdfPCell tagStatsCell = new PdfPCell(new Phrase(
                    labels.get("pdfTagDocPrefix") + tag.getDocumentCount() + " ｜ " + labels.get("pdfTagFilePrefix") + tag.getFileCount(),
                    tagFont));
            tagStatsCell.setBackgroundColor(bgColor);
            tagStatsCell.setBorderColor(new Color(203, 213, 225));
            tagStatsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            tagStatsCell.setPadding(7f);
            tagBanner.addCell(tagStatsCell);

            pdfDoc.add(tagBanner);

            if (tag.getDocuments().isEmpty()) {
                Paragraph emptyPara = new Paragraph(labels.get("emptyTagDocs"), metaFont);
                emptyPara.setIndentationLeft(0f);
                emptyPara.setSpacingAfter(6f);
                pdfDoc.add(emptyPara);
                continue;
            }

            int docIdx = 0;
            for (ReportDocumentNode doc : tag.getDocuments()) {
                docIdx++;
                StringBuilder docHeaderSb = new StringBuilder();
                docHeaderSb.append("[#").append(docIdx).append("] ")
                        .append(doc.getTitle() != null ? doc.getTitle() : labels.get("untitledDocument"));
                if (data.getOptions().isIncludeDate() && !Strings.isNullOrEmpty(doc.getFormattedCreateDate())) {
                    docHeaderSb.append("   ").append(labels.get("createDatePrefix")).append(doc.getFormattedCreateDate());
                }

                Paragraph docPara = new Paragraph(docHeaderSb.toString(), docTitleFont);
                docPara.setIndentationLeft(0f);
                docPara.setSpacingBefore(4f);
                docPara.setSpacingAfter(3f);
                pdfDoc.add(docPara);

                if (data.getOptions().isIncludeDescription() && !Strings.isNullOrEmpty(doc.getPlainDescription())) {
                    Paragraph descPara = new Paragraph(labels.get("descPrefix") + doc.getPlainDescription(), bodyFont);
                    descPara.setIndentationLeft(0f);
                    descPara.setSpacingAfter(4f);
                    pdfDoc.add(descPara);
                }

                if (!doc.getFiles().isEmpty()) {
                    boolean showSize = data.getOptions().isIncludeSize();
                    boolean showDate = data.getOptions().isIncludeDate();
                    float[] colWidths;
                    if (showSize && showDate) {
                        colWidths = new float[]{0.08f, 0.34f, 0.12f, 0.16f, 0.14f, 0.16f};
                    } else if (showSize) {
                        colWidths = new float[]{0.08f, 0.44f, 0.14f, 0.18f, 0.16f};
                    } else if (showDate) {
                        colWidths = new float[]{0.08f, 0.46f, 0.14f, 0.16f, 0.16f};
                    } else {
                        colWidths = new float[]{0.10f, 0.56f, 0.16f, 0.18f};
                    }
                    PdfPTable fileTable = new PdfPTable(colWidths);
                    fileTable.setWidthPercentage(100f);
                    fileTable.setSpacingAfter(6f);

                    addFileHeaderCell(fileTable, labels.get("colOrder"), tableHeaderFont);
                    addFileHeaderCell(fileTable, labels.get("colFileName"), tableHeaderFont);
                    addFileHeaderCell(fileTable, labels.get("colVersion"), tableHeaderFont);
                    if (showSize) {
                        addFileHeaderCell(fileTable, labels.get("colFileSize"), tableHeaderFont);
                    }
                    if (showDate) {
                        addFileHeaderCell(fileTable, labels.get("colCreateDate"), tableHeaderFont);
                    }
                    addFileHeaderCell(fileTable, labels.get("colFileLink"), tableHeaderFont);

                    int fileIdx = 0;
                    for (ReportFileNode file : doc.getFiles()) {
                        fileIdx++;
                        addFileBodyCell(fileTable, String.valueOf(fileIdx), bodyFont);
                        addFileBodyCell(fileTable, file.getName(), bodyFont);
                        addFileBodyCell(fileTable, file.getVersionDisplay(), bodyFont);
                        if (showSize) {
                            addFileBodyCell(fileTable, file.getFormattedSize(), bodyFont);
                        }
                        if (showDate) {
                            addFileBodyCell(fileTable, file.getFormattedCreateDate(), bodyFont);
                        }

                        String actionLabel = "download".equals(file.getEffectiveLinkMode()) ? labels.get("downloadFile") : labels.get("previewFile");
                        Chunk linkChunk = new Chunk(actionLabel, linkFont);
                        linkChunk.setAnchor(file.getEffectiveUrl());
                        PdfPCell linkCell = new PdfPCell(new Phrase(linkChunk));
                        linkCell.setBorderColor(new Color(226, 232, 240));
                        linkCell.setPadding(5f);
                        fileTable.addCell(linkCell);
                    }
                    pdfDoc.add(fileTable);
                }
            }
        }

        pdfDoc.close();
    }

    /**
     * Returns localized label map for Excel, HTML, and PDF exports based on language code.
     */
    private static Map<String, String> getReportLabels(String lang) {
        String normalized = lang != null ? lang.trim().toLowerCase(Locale.ROOT) : "zh_tw";
        Map<String, String> m = new HashMap<>();
        if (normalized.startsWith("zh_cn") || normalized.startsWith("zh-cn") || normalized.startsWith("zh-hans")) {
            m.put("htmlLang", "zh-Hans");
            m.put("exportedAtPrefix", "导出时间：");
            m.put("linkModePrefix", "文件链接模式：");
            m.put("excelLinkModePrefix", "链接模式：");
            m.put("previewFile", "预览文件");
            m.put("downloadFile", "下载文件");
            m.put("kpiSelectedTags", "选取标签数");
            m.put("kpiTagDocLinks", "标签文档挂载总次数");
            m.put("kpiUniqueDocs", "不重复文档总数");
            m.put("kpiUniqueFiles", "不重复文件总数");
            m.put("sheetSummary", "统计总览");
            m.put("sheetDetails", "标签文档明细");
            m.put("colHierarchyCode", "层级编号");
            m.put("colTagName", "标签名称");
            m.put("colDirectDocCount", "直接挂载文档数");
            m.put("colDirectFileCount", "直接挂载文件数");
            m.put("colItemType", "项目类型");
            m.put("colHierarchyOrder", "层级编号 / 排序");
            m.put("colEntityName", "标签 / 文档 / 文件名称");
            m.put("colVersion", "版本");
            m.put("colCountSummary", "数量摘要");
            m.put("colFileSize", "文件大小");
            m.put("colCreateUpdateDate", "创建 / 更新日期");
            m.put("colDescription", "文档说明");
            m.put("colFileLink", "文件链接");
            m.put("colFileAction", "文件操作");
            m.put("colOrder", "排序");
            m.put("colFileName", "文件名称");
            m.put("colCreateDate", "创建日期");
            m.put("typeRootTag", "根标签");
            m.put("typeChildTag", "子标签");
            m.put("typeDocument", "文档");
            m.put("typeFile", "文件");
            m.put("untitledDocument", "未命名文档");
            m.put("emptyTagDocs", "此标签下无直接挂载文档");
            m.put("emptyDocFiles", "无附件文件");
            m.put("tagDocCountPrefix", "文档数：");
            m.put("tagFileCountPrefix", "文件数：");
            m.put("pdfTagDocPrefix", "文档：");
            m.put("pdfTagFilePrefix", "文件：");
            m.put("excelTagDocPrefix", "文档数: ");
            m.put("excelTagFilePrefix", " / 文件数: ");
            m.put("excelDocFilesPrefix", "附件数: ");
            m.put("excelUpdatedPrefix", " (更新: ");
            m.put("createDatePrefix", "创建日期：");
            m.put("updateDatePrefix", "更新日期：");
            m.put("descPrefix", "说明：");
        } else if (normalized.startsWith("zh")) {
            m.put("htmlLang", "zh-Hant");
            m.put("exportedAtPrefix", "匯出時間：");
            m.put("linkModePrefix", "檔案連結模式：");
            m.put("excelLinkModePrefix", "連結模式：");
            m.put("previewFile", "預覽檔案");
            m.put("downloadFile", "下載檔案");
            m.put("kpiSelectedTags", "選取標籤數");
            m.put("kpiTagDocLinks", "標籤文件掛載總次數");
            m.put("kpiUniqueDocs", "不重複文件總數");
            m.put("kpiUniqueFiles", "不重複檔案總數");
            m.put("sheetSummary", "統計總覽");
            m.put("sheetDetails", "標籤文件明細");
            m.put("colHierarchyCode", "階層編號");
            m.put("colTagName", "標籤名稱");
            m.put("colDirectDocCount", "直接掛載文件數");
            m.put("colDirectFileCount", "直接掛載檔案數");
            m.put("colItemType", "項目類型");
            m.put("colHierarchyOrder", "階層編號 / 排序");
            m.put("colEntityName", "標籤 / 文件 / 檔案名稱");
            m.put("colVersion", "版本");
            m.put("colCountSummary", "數量摘要");
            m.put("colFileSize", "檔案大小");
            m.put("colCreateUpdateDate", "建立 / 更新日期");
            m.put("colDescription", "文件說明");
            m.put("colFileLink", "檔案連結");
            m.put("colFileAction", "檔案操作");
            m.put("colOrder", "排序");
            m.put("colFileName", "檔案名稱");
            m.put("colCreateDate", "建立日期");
            m.put("typeRootTag", "根標籤");
            m.put("typeChildTag", "子標籤");
            m.put("typeDocument", "文件");
            m.put("typeFile", "檔案");
            m.put("untitledDocument", "未命名文件");
            m.put("emptyTagDocs", "此標籤下無直接掛載文件");
            m.put("emptyDocFiles", "無附件檔案");
            m.put("tagDocCountPrefix", "文件數：");
            m.put("tagFileCountPrefix", "檔案數：");
            m.put("pdfTagDocPrefix", "文件：");
            m.put("pdfTagFilePrefix", "檔案：");
            m.put("excelTagDocPrefix", "文件數: ");
            m.put("excelTagFilePrefix", " / 檔案數: ");
            m.put("excelDocFilesPrefix", "附件數: ");
            m.put("excelUpdatedPrefix", " (更新: ");
            m.put("createDatePrefix", "建立日期：");
            m.put("updateDatePrefix", "更新日期：");
            m.put("descPrefix", "說明：");
        } else {
            m.put("htmlLang", "en");
            m.put("exportedAtPrefix", "Exported At: ");
            m.put("linkModePrefix", "File Link Mode: ");
            m.put("excelLinkModePrefix", "Link Mode: ");
            m.put("previewFile", "Preview File");
            m.put("downloadFile", "Download File");
            m.put("kpiSelectedTags", "Selected Tags");
            m.put("kpiTagDocLinks", "Total Tag-Doc Links");
            m.put("kpiUniqueDocs", "Unique Documents");
            m.put("kpiUniqueFiles", "Unique Files");
            m.put("sheetSummary", "Summary");
            m.put("sheetDetails", "Tag Document Details");
            m.put("colHierarchyCode", "Hierarchy Code");
            m.put("colTagName", "Tag Name");
            m.put("colDirectDocCount", "Direct Documents");
            m.put("colDirectFileCount", "Direct Files");
            m.put("colItemType", "Item Type");
            m.put("colHierarchyOrder", "Hierarchy / Order");
            m.put("colEntityName", "Tag / Document / File Name");
            m.put("colVersion", "Version");
            m.put("colCountSummary", "Count Summary");
            m.put("colFileSize", "File Size");
            m.put("colCreateUpdateDate", "Created / Updated Date");
            m.put("colDescription", "Description");
            m.put("colFileLink", "File Link");
            m.put("colFileAction", "Action");
            m.put("colOrder", "Order");
            m.put("colFileName", "File Name");
            m.put("colCreateDate", "Created Date");
            m.put("typeRootTag", "Root Tag");
            m.put("typeChildTag", "Child Tag");
            m.put("typeDocument", "Document");
            m.put("typeFile", "File");
            m.put("untitledDocument", "Untitled Document");
            m.put("emptyTagDocs", "No documents directly attached to this tag");
            m.put("emptyDocFiles", "No attached files");
            m.put("tagDocCountPrefix", "Documents: ");
            m.put("tagFileCountPrefix", "Files: ");
            m.put("pdfTagDocPrefix", "Docs: ");
            m.put("pdfTagFilePrefix", "Files: ");
            m.put("excelTagDocPrefix", "Docs: ");
            m.put("excelTagFilePrefix", " / Files: ");
            m.put("excelDocFilesPrefix", "Files: ");
            m.put("excelUpdatedPrefix", " (Updated: ");
            m.put("createDatePrefix", "Created: ");
            m.put("updateDatePrefix", "Updated: ");
            m.put("descPrefix", "Description: ");
        }
        return m;
    }

    private static void addKpiCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(248, 250, 252));
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setPadding(6f);
        Paragraph p1 = new Paragraph(label, labelFont);
        p1.setAlignment(Element.ALIGN_CENTER);
        Paragraph p2 = new Paragraph(value, valueFont);
        p2.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p1);
        cell.addElement(p2);
        table.addCell(cell);
    }

    private static void addFileHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private static void addFileBodyCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private static boolean hasSelectedAncestor(TagDto tag, Map<String, TagDto> visibleById, Set<String> selectedSet) {
        Set<String> visited = new HashSet<>();
        String parentId = tag.getParentId();
        while (parentId != null && visited.add(parentId)) {
            if (selectedSet.contains(parentId)) {
                return true;
            }
            TagDto parent = visibleById.get(parentId);
            parentId = parent != null ? parent.getParentId() : null;
        }
        return false;
    }

    private static ReportTagNode findNearestIncludedAncestor(TagDto tag, Map<String, TagDto> visibleById, Map<String, ReportTagNode> nodeById) {
        Set<String> visited = new HashSet<>();
        String parentId = tag.getParentId();
        while (parentId != null && visited.add(parentId)) {
            ReportTagNode ancestorNode = nodeById.get(parentId);
            if (ancestorNode != null) {
                return ancestorNode;
            }
            TagDto parentDto = visibleById.get(parentId);
            parentId = parentDto != null ? parentDto.getParentId() : null;
        }
        return null;
    }

    private static String resolveFileName(File file, String docTitle) {
        if (!Strings.isNullOrEmpty(file.getName())) {
            return file.getName();
        }
        String ext = MimeTypeUtil.getFileExtension(file.getMimeType());
        String base = !Strings.isNullOrEmpty(docTitle) ? docTitle : file.getId();
        return ext != null ? base + "." + ext : base;
    }

    private static long resolveFileSize(File file) {
        if (file.getSize() != null && file.getSize() >= 0L) {
            return file.getSize();
        }
        try {
            Path stored = DirectoryUtil.getStorageDirectory(file).resolve(file.getId());
            if (Files.exists(stored)) {
                return Files.size(stored);
            }
        } catch (Exception ignored) {
            // Fall through
        }
        return 0L;
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0L) {
            return "0 B";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.ROOT, "%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }

    private static String stripHtml(String html) {
        if (Strings.isNullOrEmpty(html)) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (Strings.isNullOrEmpty(hex)) {
            return fallback;
        }
        String clean = hex.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        try {
            if (clean.length() == 6) {
                int rgb = Integer.parseInt(clean, 16);
                return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            } else if (clean.length() == 3) {
                int r = Integer.parseInt(clean.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(clean.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(clean.substring(2, 3), 16) * 17;
                return new Color(r, g, b);
            }
        } catch (NumberFormatException ignored) {
            // Fall back
        }
        return fallback;
    }

    private static void setThinBorder(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void createStyledCell(Row row, int col, String val, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val != null ? val : "");
        cell.setCellStyle(style);
    }

    private static void createStyledCell(Row row, int col, long val, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val);
        cell.setCellStyle(style);
    }
}
