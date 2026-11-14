package com.sismics.docs.core.service;

import com.sismics.docs.BaseTransactionalTest;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.MetadataType;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.DocumentMetadataDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.MetadataDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.model.jpa.Acl;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.DocumentMetadata;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.Metadata;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.TransactionUtil;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Assert;
import org.junit.Test;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Tests for {@link TagReportService}.
 */
public class TestTagReportService extends BaseTransactionalTest {

    @Test
    public void testTagReportServiceAllFormats() throws Exception {
        User user = createUser("reportUser");
        List<String> targetIdList = Collections.singletonList(user.getId());
        AclDao aclDao = new AclDao();
        TagDao tagDao = new TagDao();
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();
        MetadataDao metadataDao = new MetadataDao();
        DocumentMetadataDao docMetadataDao = new DocumentMetadataDao();

        // Create parent tag 1 ("研發專案") and child tag 1.1 ("2026前端重構") and root tag 2 ("財務報表")
        Tag rootTag1 = new Tag();
        rootTag1.setUserId(user.getId());
        rootTag1.setName("研發專案");
        rootTag1.setColor("#1e3a5f");
        rootTag1.setOrder(0);
        String rootTag1Id = tagDao.create(rootTag1, user.getId());
        createAcl(aclDao, rootTag1Id, user.getId());

        Tag childTag1 = new Tag();
        childTag1.setUserId(user.getId());
        childTag1.setName("2026前端重構");
        childTag1.setColor("#3a87ad");
        childTag1.setParentId(rootTag1Id);
        childTag1.setOrder(0);
        String childTag1Id = tagDao.create(childTag1, user.getId());
        createAcl(aclDao, childTag1Id, user.getId());

        Tag rootTag2 = new Tag();
        rootTag2.setUserId(user.getId());
        rootTag2.setName("財務報表");
        rootTag2.setColor("#059669");
        rootTag2.setOrder(1);
        String rootTag2Id = tagDao.create(rootTag2, user.getId());
        createAcl(aclDao, rootTag2Id, user.getId());

        // Custom metadata definition
        Metadata metaDef = new Metadata();
        metaDef.setName("部門編號");
        metaDef.setType(MetadataType.STRING);
        String metaId = metadataDao.create(metaDef, user.getId());

        // Create Doc A and Doc B under rootTag1 with explicit order: Doc B (order 0) before Doc A (order 1)
        Document docA = new Document();
        docA.setUserId(user.getId());
        docA.setTitle("架構設計書A");
        docA.setDescription("<p>這是架構設計書A的詳細說明</p>");
        docA.setLanguage("zh_TW");
        docA.setCreateDate(new Date(1700000000000L));
        String docAId = documentDao.create(docA, user.getId());
        createAcl(aclDao, docAId, user.getId());

        DocumentMetadata dm = new DocumentMetadata();
        dm.setDocumentId(docAId);
        dm.setMetadataId(metaId);
        dm.setValue("RD-001");
        docMetadataDao.create(dm);

        Document docB = new Document();
        docB.setUserId(user.getId());
        docB.setTitle("優先規範書B");
        docB.setDescription("優先規範書B說明");
        docB.setLanguage("zh_TW");
        docB.setCreateDate(new Date(1700086400000L));
        String docBId = documentDao.create(docB, user.getId());
        createAcl(aclDao, docBId, user.getId());

        // Attach both docA and docB to rootTag1, and also attach docA to childTag1 (multi-tag test)
        tagDao.updateTagList(docAId, new LinkedHashSet<>(Arrays.asList(rootTag1Id, childTag1Id)));
        tagDao.updateTagList(docBId, Collections.singleton(rootTag1Id));
        // Reorder under rootTag1: docB first (index 0), docA second (index 1)
        tagDao.reorderDocumentsInTag(rootTag1Id, Arrays.asList(docBId, docAId), 0);

        // Add files to docB: file2 (order 0), file1 (order 1), and an old version (latestVersion = false)
        File oldVer = new File();
        oldVer.setDocumentId(docBId);
        oldVer.setUserId(user.getId());
        oldVer.setName("歷史舊版.pdf");
        oldVer.setMimeType("application/pdf");
        oldVer.setVersion(0);
        oldVer.setLatestVersion(false);
        oldVer.setOrder(0);
        oldVer.setSize(512L);
        fileDao.create(oldVer, user.getId());

        File fileB1 = new File();
        fileB1.setDocumentId(docBId);
        fileB1.setUserId(user.getId());
        fileB1.setName("次要附件.pdf");
        fileB1.setMimeType("application/pdf");
        fileB1.setVersion(0);
        fileB1.setLatestVersion(true);
        fileB1.setOrder(1);
        fileB1.setSize(2048L);
        String fileB1Id = fileDao.create(fileB1, user.getId());

        File fileB0 = new File();
        fileB0.setDocumentId(docBId);
        fileB0.setUserId(user.getId());
        fileB0.setName("首要附件.pdf");
        fileB0.setMimeType("application/pdf");
        fileB0.setVersion(1);
        fileB0.setVersionLabel("v2.0");
        fileB0.setLatestVersion(true);
        fileB0.setOrder(0);
        fileB0.setSize(1024L);
        String fileB0Id = fileDao.create(fileB0, user.getId());

        TransactionUtil.commit();

        TagReportService service = new TagReportService();

        // 1. Test buildReportData with includeChildren = true
        TagReportService.ExportOptions opts = new TagReportService.ExportOptions()
                .setSelectedTagIds(Arrays.asList(rootTag1Id, rootTag2Id))
                .setIncludeChildren(true)
                .setIncludeDate(false)
                .setIncludeDescription(false)
                .setLinkMode("preview")
                .setBaseUrl("http://localhost:8080")
                .setUserId(user.getId())
                .setExportedBy("reportUser")
                .setTargetIdList(targetIdList)
                .setAdminOrPrivileged(false);

        TagReportService.ReportData data = service.buildReportData(opts);
        Assert.assertEquals(3, data.getFlatTags().size());
        Assert.assertEquals("1", data.getFlatTags().get(0).getHierarchyCode());
        Assert.assertEquals("研發專案", data.getFlatTags().get(0).getName());
        Assert.assertEquals("1.1", data.getFlatTags().get(1).getHierarchyCode());
        Assert.assertEquals("2026前端重構", data.getFlatTags().get(1).getName());
        Assert.assertEquals("2", data.getFlatTags().get(2).getHierarchyCode());
        Assert.assertEquals("財務報表", data.getFlatTags().get(2).getName());

        // Check document ordering under rootTag1: docB (order 0) before docA (order 1)
        List<TagReportService.ReportDocumentNode> root1Docs = data.getFlatTags().get(0).getDocuments();
        Assert.assertEquals(2, root1Docs.size());
        Assert.assertEquals(docBId, root1Docs.get(0).getId());
        Assert.assertEquals(docAId, root1Docs.get(1).getId());

        // Check file ordering under docB: fileB0 (order 0) before fileB1 (order 1), oldVer excluded
        List<TagReportService.ReportFileNode> docBFiles = root1Docs.get(0).getFiles();
        Assert.assertEquals(2, docBFiles.size());
        Assert.assertEquals(fileB0Id, docBFiles.get(0).getId());
        Assert.assertEquals(fileB1Id, docBFiles.get(1).getId());

        // Check statistics: 3 total Tag-Document links (2 in rootTag1 + 1 in childTag1), 2 unique documents
        Assert.assertEquals(3, data.getStatistics().getTotalTagDocumentLinks());
        Assert.assertEquals(2, data.getStatistics().getUniqueDocuments());
        Assert.assertEquals(2, data.getStatistics().getUniqueFiles());
        Assert.assertEquals(3072L, data.getStatistics().getTotalSize());

        // 2. Test JSON Export: must retain dates, descriptions, metadata, preview_url and download_url even when includeDate/includeDescription=false
        ByteArrayOutputStream jsonOut = new ByteArrayOutputStream();
        service.exportJson(data, jsonOut);
        String jsonStr = jsonOut.toString(StandardCharsets.UTF_8);
        JsonObject jsonObj = Json.createReader(new StringReader(jsonStr)).readObject();
        Assert.assertEquals(3, jsonObj.getJsonObject("statistics").getInt("total_tags"));
        JsonArray rootTagsJson = jsonObj.getJsonArray("tags");
        Assert.assertEquals(2, rootTagsJson.size());
        JsonObject firstRootJson = rootTagsJson.getJsonObject(0);
        Assert.assertEquals("1", firstRootJson.getString("hierarchy_code"));
        Assert.assertEquals(1, firstRootJson.getJsonArray("children").size());
        Assert.assertEquals("1.1", firstRootJson.getJsonArray("children").getJsonObject(0).getString("hierarchy_code"));
        JsonObject docAJson = firstRootJson.getJsonArray("documents").getJsonObject(1);
        Assert.assertEquals("<p>這是架構設計書A的詳細說明</p>", docAJson.getString("description"));
        Assert.assertFalse(docAJson.isNull("create_date"));
        Assert.assertEquals("RD-001", docAJson.getJsonArray("metadata").getJsonObject(0).getString("value"));
        JsonObject file0Json = firstRootJson.getJsonArray("documents").getJsonObject(0).getJsonArray("files").getJsonObject(0);
        Assert.assertEquals("http://localhost:8080/#/document/" + docBId + "/file/" + fileB0Id, file0Json.getString("preview_url"));
        Assert.assertEquals("http://localhost:8080/api/file/" + fileB0Id + "/data", file0Json.getString("download_url"));

        // 2b. Test JSON ZIP Export: contains both .json and English README.md
        ByteArrayOutputStream jsonZipOut = new ByteArrayOutputStream();
        service.exportJsonZip(data, jsonZipOut, "tag-report-test.json");
        java.util.Map<String, String> zipEntries = new java.util.HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jsonZipOut.toByteArray()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zipEntries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        Assert.assertTrue(zipEntries.containsKey("tag-report-test.json"));
        Assert.assertTrue(zipEntries.containsKey("README.md"));
        Assert.assertTrue(zipEntries.get("README.md").contains("# Tag Report JSON Export Schema"));
        Assert.assertTrue(zipEntries.get("README.md").contains("hierarchy_code"));
        Assert.assertTrue(zipEntries.get("README.md").contains("effective_url"));
        JsonObject zippedJsonObj = Json.createReader(new StringReader(zipEntries.get("tag-report-test.json"))).readObject();
        Assert.assertEquals(3, zippedJsonObj.getJsonObject("statistics").getInt("total_tags"));

        // 3. Test Excel Export: 2 sheets, hyperlinks, respects includeDate=false & includeDescription=false
        ByteArrayOutputStream xlsxOut = new ByteArrayOutputStream();
        service.exportExcel(data, xlsxOut);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxOut.toByteArray()))) {
            Assert.assertEquals(2, wb.getNumberOfSheets());
            Assert.assertEquals("統計總覽", wb.getSheetName(0));
            Assert.assertEquals("標籤文件明細", wb.getSheetName(1));
            XSSFSheet summarySheet = wb.getSheetAt(0);
            for (Row r : summarySheet) {
                for (Cell c : r) {
                    if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        Assert.assertFalse("Tag color column should be omitted from Excel summary", c.getStringCellValue().contains("標籤顏色"));
                    }
                }
            }
            XSSFSheet detailSheet = wb.getSheetAt(1);
            Row headerRow = detailSheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }
            Assert.assertTrue("Version header should be '版本'", headers.contains("版本"));
            Assert.assertFalse("Color header should be omitted", headers.toString().contains("顏色"));
            Assert.assertFalse("Date column should be omitted when includeDate=false", headers.contains("建立 / 更新日期"));
            Assert.assertFalse("Description column should be omitted when includeDescription=false", headers.contains("文件說明"));
            Assert.assertFalse("Size column should be omitted when includeSize=false", headers.contains("檔案大小"));
            Assert.assertFalse("MIME type column should be omitted", headers.toString().contains("MIME"));
            // Verify tag row has empty version cell (no color)
            Row tagRow = detailSheet.getRow(1);
            Assert.assertEquals("", tagRow.getCell(3).getStringCellValue());
            // Verify hyperlink on first file row (row 0 = header, row 1 = rootTag1, row 2 = docB, row 3 = fileB0)
            Row fileRow = detailSheet.getRow(3);
            Cell linkCell = fileRow.getCell(headers.size() - 1);
            Assert.assertEquals("預覽檔案", linkCell.getStringCellValue());
            Assert.assertNotNull(linkCell.getHyperlink());
            Assert.assertEquals("http://localhost:8080/#/document/" + docBId + "/file/" + fileB0Id, linkCell.getHyperlink().getAddress());
        }

        // 4. Test HTML Export: fixed-width 1040px, flush-left hierarchy code, respects includeDate=false, includeDescription=false, includeSize=false
        ByteArrayOutputStream htmlOut = new ByteArrayOutputStream();
        service.exportHtml(data, htmlOut);
        String htmlStr = htmlOut.toString(StandardCharsets.UTF_8);
        Assert.assertTrue(htmlStr.contains("width: 1040px;"));
        Assert.assertTrue(htmlStr.contains("1.1"));
        Assert.assertTrue(htmlStr.contains("2026前端重構"));
        Assert.assertFalse(htmlStr.contains("標籤階層報表"));
        Assert.assertFalse(htmlStr.contains("匯出人員"));
        Assert.assertFalse(htmlStr.contains("檔案總容量"));
        Assert.assertFalse(htmlStr.contains("容量小計"));
        Assert.assertFalse(htmlStr.contains("MIME 類型"));
        Assert.assertFalse(htmlStr.contains("檔案大小"));
        Assert.assertFalse(htmlStr.contains("建立者："));
        Assert.assertFalse(htmlStr.contains("語言："));
        Assert.assertFalse("Description should be hidden when includeDescription=false", htmlStr.contains("這是架構設計書A的詳細說明"));
        Assert.assertTrue(htmlStr.contains("http://localhost:8080/#/document/" + docBId + "/file/" + fileB0Id));

        // 5. Test PDF Export with download link mode & includeSize=true: CJK text + clickable PDAnnotationLink
        opts.setIncludeDate(true).setIncludeDescription(true).setIncludeSize(true).setLinkMode("download");
        TagReportService.ReportData dataWithAll = service.buildReportData(opts);
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        service.exportPdf(dataWithAll, pdfOut);
        byte[] pdfBytes = pdfOut.toByteArray();
        Assert.assertTrue(pdfBytes.length > 500);
        try (PDDocument pdDoc = PDDocument.load(pdfBytes)) {
            Assert.assertTrue(pdDoc.getNumberOfPages() >= 1);
            String pdfText = new PDFTextStripper().getText(pdDoc);
            Assert.assertTrue(pdfText.contains("研發專案"));
            Assert.assertTrue(pdfText.contains("2026前端重構"));
            Assert.assertTrue(pdfText.contains("優先規範書B"));
            Assert.assertTrue(pdfText.contains("檔案大小"));
            Assert.assertFalse(pdfText.contains("標籤階層報表"));
            Assert.assertFalse(pdfText.contains("匯出人員"));
            Assert.assertFalse(pdfText.contains("檔案總容量"));
            Assert.assertFalse(pdfText.contains("建立者："));
            Assert.assertFalse(pdfText.contains("語言："));

            List<String> linkUris = new ArrayList<>();
            for (PDPage page : pdDoc.getPages()) {
                for (PDAnnotation ann : page.getAnnotations()) {
                    if (ann instanceof PDAnnotationLink) {
                        PDAnnotationLink link = (PDAnnotationLink) ann;
                        if (link.getAction() instanceof PDActionURI) {
                            linkUris.add(((PDActionURI) link.getAction()).getURI());
                        }
                    }
                }
            }
            Assert.assertTrue(linkUris.contains("http://localhost:8080/api/file/" + fileB0Id + "/data"));
        }

        // 6. Test includeChildren = false: selecting rootTag1 only should NOT include childTag1
        opts.setSelectedTagIds(Collections.singletonList(rootTag1Id)).setIncludeChildren(false);
        TagReportService.ReportData rootOnlyData = service.buildReportData(opts);
        Assert.assertEquals(1, rootOnlyData.getFlatTags().size());
        Assert.assertEquals(rootTag1Id, rootOnlyData.getFlatTags().get(0).getId());

        // 7. Test English language (lang="en") across Excel, HTML, and PDF
        opts.setLang("en").setIncludeDate(true).setIncludeDescription(true).setIncludeSize(true);
        TagReportService.ReportData enData = service.buildReportData(opts);

        ByteArrayOutputStream enXlsxOut = new ByteArrayOutputStream();
        service.exportExcel(enData, enXlsxOut);
        try (XSSFWorkbook enWb = new XSSFWorkbook(new ByteArrayInputStream(enXlsxOut.toByteArray()))) {
            Assert.assertEquals("Summary", enWb.getSheetName(0));
            Assert.assertEquals("Tag Document Details", enWb.getSheetName(1));
            Assert.assertEquals("Item Type", enWb.getSheetAt(1).getRow(0).getCell(0).getStringCellValue());
        }

        ByteArrayOutputStream enHtmlOut = new ByteArrayOutputStream();
        service.exportHtml(enData, enHtmlOut);
        String enHtmlStr = enHtmlOut.toString(StandardCharsets.UTF_8);
        Assert.assertTrue(enHtmlStr.contains("lang=\"en\""));
        Assert.assertTrue(enHtmlStr.contains("Exported At: "));
        Assert.assertTrue(enHtmlStr.contains("Selected Tags"));
        Assert.assertFalse(enHtmlStr.contains("匯出時間："));

        ByteArrayOutputStream enPdfOut = new ByteArrayOutputStream();
        service.exportPdf(enData, enPdfOut);
        try (PDDocument enPdDoc = PDDocument.load(enPdfOut.toByteArray())) {
            String enPdfText = new PDFTextStripper().getText(enPdDoc);
            Assert.assertTrue(enPdfText.contains("Exported At:"));
            Assert.assertTrue(enPdfText.contains("Selected Tags"));
            Assert.assertFalse(enPdfText.contains("匯出時間："));
        }
    }

    private void createAcl(AclDao aclDao, String sourceId, String targetId) {
        Acl aclRead = new Acl();
        aclRead.setSourceId(sourceId);
        aclRead.setPerm(PermType.READ);
        aclRead.setTargetId(targetId);
        aclRead.setType(AclType.USER);
        aclDao.create(aclRead, targetId);

        Acl aclWrite = new Acl();
        aclWrite.setSourceId(sourceId);
        aclWrite.setPerm(PermType.WRITE);
        aclWrite.setTargetId(targetId);
        aclWrite.setType(AclType.USER);
        aclDao.create(aclWrite, targetId);
    }
}
