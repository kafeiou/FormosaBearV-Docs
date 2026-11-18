package com.sismics.docs.core.util.plugin.exam;

import com.google.common.base.Strings;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.PdfUtil;
import fr.opensagres.xdocreport.itext.extension.font.IFontProvider;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates localized blank exam PDFs (no watermark), completed exam PDFs (with exam-time watermark, no correct answers),
 * and landscape summary score report PDFs.
 */
public class ExamPdfExporter {
    private static final Map<String, String> ZH_TW = Map.ofEntries(
            Map.entry("exam_code", "考卷代號"),
            Map.entry("version", "版號"),
            Map.entry("blank_paper", "空白考卷"),
            Map.entry("name_line", "姓名：_______________________"),
            Map.entry("date_line", "日期：_______________________"),
            Map.entry("score", "分數"),
            Map.entry("examinee", "應試人員"),
            Map.entry("exam_time", "應試時間"),
            Map.entry("author", "出題來源"),
            Map.entry("type_single", "[單選題]"),
            Map.entry("type_multi", "[多選題]"),
            Map.entry("type_tf", "[是非題]"),
            Map.entry("correct", "   [✓ 正確]"),
            Map.entry("incorrect", "   [✗ 錯誤]"),
            Map.entry("true_opt", "是 (True)"),
            Map.entry("false_opt", "否 (False)"),
            Map.entry("summary_title", "線上考試成績總覽彙總表"),
            Map.entry("date_range", "統計區間"),
            Map.entry("exam_filter", "考卷篩選"),
            Map.entry("all_exams", "全部考卷"),
            Map.entry("exported_by", "匯出人員"),
            Map.entry("exported_at", "匯出時間"),
            Map.entry("total_attempts", "總應試人次"),
            Map.entry("avg_score", "平均分數"),
            Map.entry("max_score", "最高分數"),
            Map.entry("min_score", "最低分數"),
            Map.entry("col_code_ver", "代號 / 版號"),
            Map.entry("col_title", "考卷名稱"),
            Map.entry("col_owner", "出題老師 / 科別"),
            Map.entry("col_examinee", "應試人員"),
            Map.entry("col_score", "分數 / 滿分"),
            Map.entry("col_time", "交卷時間"),
            Map.entry("no_records", "此時間區間內無符合條件之成績紀錄。")
    );

    private static final Map<String, String> EN = Map.ofEntries(
            Map.entry("exam_code", "Exam Code"),
            Map.entry("version", "Version"),
            Map.entry("blank_paper", "Blank Exam Paper"),
            Map.entry("name_line", "Name: _______________________"),
            Map.entry("date_line", "Date: _______________________"),
            Map.entry("score", "Score"),
            Map.entry("examinee", "Examinee"),
            Map.entry("exam_time", "Exam Time"),
            Map.entry("author", "Author"),
            Map.entry("type_single", "[Single Choice]"),
            Map.entry("type_multi", "[Multiple Choice]"),
            Map.entry("type_tf", "[True / False]"),
            Map.entry("correct", "   [✓ Correct]"),
            Map.entry("incorrect", "   [✗ Incorrect]"),
            Map.entry("true_opt", "True"),
            Map.entry("false_opt", "False"),
            Map.entry("summary_title", "Online Exam Score Summary Report"),
            Map.entry("date_range", "Date Range"),
            Map.entry("exam_filter", "Exam Filter"),
            Map.entry("all_exams", "All Exams"),
            Map.entry("exported_by", "Exported By"),
            Map.entry("exported_at", "Exported At"),
            Map.entry("total_attempts", "Total Attempts"),
            Map.entry("avg_score", "Average Score"),
            Map.entry("max_score", "Highest Score"),
            Map.entry("min_score", "Lowest Score"),
            Map.entry("col_code_ver", "Code / Version"),
            Map.entry("col_title", "Exam Title"),
            Map.entry("col_owner", "Author / Group"),
            Map.entry("col_examinee", "Examinee"),
            Map.entry("col_score", "Score / Max"),
            Map.entry("col_time", "Submitted Time"),
            Map.entry("no_records", "No exam score records found in this date range.")
    );

    private static String msg(String lang, String key) {
        if (lang != null && lang.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return ZH_TW.getOrDefault(key, EN.getOrDefault(key, key));
        }
        return EN.getOrDefault(key, key);
    }

    public static byte[] exportBlankPdf(JsonObject exam) throws Exception {
        return exportBlankPdf(exam, "en");
    }

    public static byte[] exportBlankPdf(JsonObject exam, String lang) throws Exception {
        return renderPdf(exam, null, lang);
    }

    public static byte[] exportAttemptPdf(JsonObject exam, JsonObject attempt) throws Exception {
        return exportAttemptPdf(exam, attempt, "en");
    }

    public static byte[] exportAttemptPdf(JsonObject exam, JsonObject attempt, String lang) throws Exception {
        return renderPdf(exam, attempt, lang);
    }

    public static byte[] exportSummaryPdf(List<JsonObject> attempts, String fromDate, String toDate,
                                          String exportedBy, String examFilterLabel, String lang) throws Exception {
        IFontProvider fontProvider = PdfUtil.getFontProvider();
        Font titleFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 16f, Font.BOLD, new Color(25, 35, 55));
        Font metaFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9.5f, Font.NORMAL, new Color(75, 85, 100));
        Font kpiLabelFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9f, Font.NORMAL, new Color(90, 100, 115));
        Font kpiValFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 13f, Font.BOLD, new Color(30, 64, 175));
        Font thFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9.5f, Font.BOLD, Color.WHITE);
        Font tdFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9.5f, Font.NORMAL, new Color(30, 41, 59));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 34, 34, 38, 38);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Paragraph title = new Paragraph(msg(lang, "summary_title"), titleFont);
        title.setSpacingAfter(6f);
        doc.add(title);

        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(10f);
        addMetaCell(metaTable, msg(lang, "date_range") + ": " + fromDate + " ~ " + toDate, metaFont);
        addMetaCell(metaTable, msg(lang, "exam_filter") + ": " + (Strings.isNullOrEmpty(examFilterLabel) ? msg(lang, "all_exams") : examFilterLabel), metaFont);
        addMetaCell(metaTable, msg(lang, "exported_by") + ": " + exportedBy, metaFont);
        addMetaCell(metaTable, msg(lang, "exported_at") + ": " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(new Date()), metaFont);
        doc.add(metaTable);

        int count = attempts.size();
        double sum = 0.0;
        double max = count > 0 ? Double.NEGATIVE_INFINITY : 0.0;
        double min = count > 0 ? Double.POSITIVE_INFINITY : 0.0;
        for (JsonObject a : attempts) {
            double s = a.getJsonNumber("score").doubleValue();
            sum += s;
            if (s > max) max = s;
            if (s < min) min = s;
        }
        double avg = count > 0 ? sum / count : 0.0;

        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(12f);
        addKpiCell(kpiTable, msg(lang, "total_attempts"), String.valueOf(count), kpiLabelFont, kpiValFont);
        addKpiCell(kpiTable, msg(lang, "avg_score"), String.format(Locale.ENGLISH, "%.2f", avg), kpiLabelFont, kpiValFont);
        addKpiCell(kpiTable, msg(lang, "max_score"), String.format(Locale.ENGLISH, "%.2f", max), kpiLabelFont, kpiValFont);
        addKpiCell(kpiTable, msg(lang, "min_score"), String.format(Locale.ENGLISH, "%.2f", min), kpiLabelFont, kpiValFont);
        doc.add(kpiTable);

        if (attempts.isEmpty()) {
            Paragraph empty = new Paragraph(msg(lang, "no_records"), metaFont);
            empty.setSpacingBefore(10f);
            doc.add(empty);
        } else {
            PdfPTable table = new PdfPTable(new float[]{1.4f, 2.2f, 2.0f, 1.9f, 1.3f, 1.6f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            String[] headers = {
                    msg(lang, "col_code_ver"),
                    msg(lang, "col_title"),
                    msg(lang, "col_owner"),
                    msg(lang, "col_examinee"),
                    msg(lang, "col_score"),
                    msg(lang, "col_time")
            };
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, thFont));
                cell.setBackgroundColor(new Color(30, 58, 138));
                cell.setPadding(6f);
                cell.setBorderColor(new Color(203, 213, 225));
                table.addCell(cell);
            }

            SimpleDateFormat timeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
            boolean zebra = false;
            for (JsonObject att : attempts) {
                Color rowBg = zebra ? new Color(248, 250, 252) : Color.WHITE;
                zebra = !zebra;
                String codeVer = att.getString("exam_code", "-") + " v" + att.getString("exam_version", "-");
                String eTitle = att.getString("exam_title", "-");
                String ownerLabel = att.getString("owner_label", att.getString("owner_username", "-"));
                String examinee = att.getString("display_name", att.getString("username", "")) + " (" + att.getString("username", "") + ")";
                String scoreStr = String.format(Locale.ENGLISH, "%.2f / %.2f",
                        att.getJsonNumber("score").doubleValue(),
                        att.getJsonNumber("max_score").doubleValue());
                long ts = att.containsKey("submitted_at") ? att.getJsonNumber("submitted_at").longValue() : 0L;
                String tsStr = ts > 0 ? timeFmt.format(new Date(ts)) : "-";

                addRowCell(table, codeVer, tdFont, rowBg);
                addRowCell(table, eTitle, tdFont, rowBg);
                addRowCell(table, ownerLabel, tdFont, rowBg);
                addRowCell(table, examinee, tdFont, rowBg);
                addRowCell(table, scoreStr, tdFont, rowBg);
                addRowCell(table, tsStr, tdFont, rowBg);
            }
            doc.add(table);
        }

        doc.close();
        return baos.toByteArray();
    }

    private static byte[] renderPdf(JsonObject exam, JsonObject attempt, String lang) throws Exception {
        IFontProvider fontProvider = PdfUtil.getFontProvider();
        Font titleFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 16f, Font.BOLD, new Color(25, 35, 55));
        Font metaFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10f, Font.NORMAL, new Color(80, 90, 105));
        Font qFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 11f, Font.BOLD, Color.BLACK);
        Font optFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10.5f, Font.NORMAL, new Color(40, 40, 40));
        Font correctFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10f, Font.BOLD, new Color(22, 130, 62));
        Font wrongFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10f, Font.BOLD, new Color(195, 45, 45));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 46, 46);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        if (attempt != null) {
            String watermarkText = buildWatermarkText(attempt);
            BaseFont watermarkBaseFont = optFont.getBaseFont();
            writer.setPageEvent(new WatermarkPageEvent(watermarkBaseFont, watermarkText));
        }

        doc.open();

        String title = exam.getString("title", "Online Exam");
        String examCode = exam.getString("exam_code", "-");
        String examVersion = exam.getString("exam_version", "-");

        Paragraph header = new Paragraph(title, titleFont);
        header.setSpacingAfter(6f);
        doc.add(header);

        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(14f);
        addMetaCell(metaTable, msg(lang, "exam_code") + ": " + examCode + "   |   " + msg(lang, "version") + ": " + examVersion, metaFont);
        if (attempt != null) {
            String displayName = attempt.getString("display_name", attempt.getString("username", ""));
            String username = attempt.getString("username", "");
            long submittedAt = attempt.containsKey("submitted_at") ? attempt.getJsonNumber("submitted_at").longValue() : System.currentTimeMillis();
            String formattedDate = formatEnglishDate(submittedAt);
            double score = attempt.getJsonNumber("score").doubleValue();
            double maxScore = attempt.getJsonNumber("max_score").doubleValue();
            addMetaCell(metaTable, String.format(Locale.ENGLISH, "%s: %.2f / %.2f", msg(lang, "score"), score, maxScore), metaFont);
            addMetaCell(metaTable, msg(lang, "examinee") + ": " + displayName + " (" + username + ")", metaFont);
            addMetaCell(metaTable, msg(lang, "exam_time") + ": " + formattedDate, metaFont);
        } else {
            addMetaCell(metaTable, msg(lang, "blank_paper"), metaFont);
            addMetaCell(metaTable, msg(lang, "name_line"), metaFont);
            addMetaCell(metaTable, msg(lang, "date_line"), metaFont);
        }
        doc.add(metaTable);

        JsonObject resultsMap = attempt != null && attempt.containsKey("results")
                ? attempt.getJsonObject("results")
                : null;
        JsonObject answersMap = attempt != null && attempt.containsKey("answers")
                ? attempt.getJsonObject("answers")
                : null;

        JsonArray questions = exam.getJsonArray("questions");
        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                JsonObject q = questions.getJsonObject(i);
                String qId = q.getString("id", "q" + (i + 1));
                String qType = q.getString("type", GiftParser.TYPE_SINGLE_CHOICE);
                String qPrompt = q.getString("prompt", "");
                String typeBadge = GiftParser.TYPE_MULTIPLE_CHOICE.equals(qType) ? msg(lang, "type_multi")
                        : (GiftParser.TYPE_TRUE_FALSE.equals(qType) ? msg(lang, "type_tf") : msg(lang, "type_single"));

                Paragraph qPara = new Paragraph();
                qPara.setSpacingBefore(6f);
                qPara.setSpacingAfter(4f);
                qPara.add(new Phrase((i + 1) + ". " + typeBadge + " " + qPrompt, qFont));

                if (resultsMap != null && resultsMap.containsKey(qId)) {
                    boolean isCorrect = resultsMap.getJsonObject(qId).getBoolean("correct", false);
                    qPara.add(new Phrase(isCorrect ? msg(lang, "correct") : msg(lang, "incorrect"), isCorrect ? correctFont : wrongFont));
                }
                doc.add(qPara);

                Set<String> selectedIds = extractSelectedIds(answersMap, qId);

                if (GiftParser.TYPE_TRUE_FALSE.equals(qType)) {
                    String tfMarkTrue = selectedIds.contains("true") ? "[■] " : "[  ] ";
                    String tfMarkFalse = selectedIds.contains("false") ? "[■] " : "[  ] ";
                    Paragraph pTrue = new Paragraph("    " + tfMarkTrue + msg(lang, "true_opt"), optFont);
                    Paragraph pFalse = new Paragraph("    " + tfMarkFalse + msg(lang, "false_opt"), optFont);
                    pFalse.setSpacingAfter(6f);
                    doc.add(pTrue);
                    doc.add(pFalse);
                } else {
                    JsonArray choices = q.getJsonArray("choices");
                    if (choices != null) {
                        for (int cIdx = 0; cIdx < choices.size(); cIdx++) {
                            JsonObject choice = choices.getJsonObject(cIdx);
                            String cId = choice.getString("id", "");
                            String cText = choice.getString("text", "");
                            String mark = selectedIds.contains(cId) ? "[■] " : "[  ] ";
                            char letter = (char) ('A' + cIdx);
                            Paragraph cPara = new Paragraph("    " + mark + letter + ". " + cText, optFont);
                            if (cIdx == choices.size() - 1) {
                                cPara.setSpacingAfter(6f);
                            }
                            doc.add(cPara);
                        }
                    }
                }
            }
        }

        doc.close();
        return baos.toByteArray();
    }

    private static void addMetaCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(220, 225, 232));
        cell.setPadding(6f);
        cell.setBackgroundColor(new Color(248, 250, 252));
        table.addCell(cell);
    }

    private static void addKpiCell(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setBackgroundColor(new Color(239, 246, 255));
        cell.setPadding(8f);
        Paragraph p1 = new Paragraph(label, labelFont);
        Paragraph p2 = new Paragraph(value, valFont);
        cell.addElement(p1);
        cell.addElement(p2);
        table.addCell(cell);
    }

    private static void addRowCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6f);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }

    private static Set<String> extractSelectedIds(JsonObject answersMap, String qId) {
        Set<String> selected = new HashSet<>();
        if (answersMap == null || !answersMap.containsKey(qId)) {
            return selected;
        }
        JsonValue val = answersMap.get(qId);
        if (val.getValueType() == JsonValue.ValueType.STRING) {
            selected.add(answersMap.getString(qId).toLowerCase(Locale.ROOT));
        } else if (val.getValueType() == JsonValue.ValueType.TRUE) {
            selected.add("true");
        } else if (val.getValueType() == JsonValue.ValueType.FALSE) {
            selected.add("false");
        } else if (val.getValueType() == JsonValue.ValueType.ARRAY) {
            JsonArray arr = val.asJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                selected.add(arr.getString(i));
            }
        }
        return selected;
    }

    private static String buildWatermarkText(JsonObject attempt) {
        String displayName = attempt.getString("display_name", attempt.getString("username", "Examinee"));
        String username = attempt.getString("username", "");
        long submittedAt = attempt.containsKey("submitted_at")
                ? attempt.getJsonNumber("submitted_at").longValue()
                : System.currentTimeMillis();
        return displayName + " (" + username + ") · " + formatEnglishDate(submittedAt);
    }

    private static String formatEnglishDate(long epochMillis) {
        String pattern = ConfigUtil.getPreviewWatermarkDateFormat();
        try {
            return new SimpleDateFormat(pattern, Locale.ENGLISH).format(new Date(epochMillis));
        } catch (Exception e) {
            return new SimpleDateFormat(ConfigUtil.DEFAULT_PREVIEW_WATERMARK_DATE_FORMAT, Locale.ENGLISH).format(new Date(epochMillis));
        }
    }

    private static class WatermarkPageEvent extends PdfPageEventHelper {
        private final BaseFont baseFont;
        private final String text;

        WatermarkPageEvent(BaseFont baseFont, String text) {
            this.baseFont = baseFont;
            this.text = text;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            canvas.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.16f);
            canvas.setGState(gs);
            canvas.beginText();
            canvas.setFontAndSize(baseFont, 18f);
            canvas.setColorFill(new Color(95, 105, 120));
            float width = document.getPageSize().getWidth();
            float height = document.getPageSize().getHeight();
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.30f, height * 0.28f, 35f);
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.70f, height * 0.28f, 35f);
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.30f, height * 0.72f, 35f);
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.70f, height * 0.72f, 35f);
            canvas.endText();
            canvas.restoreState();
        }
    }
}
