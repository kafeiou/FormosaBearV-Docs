package com.sismics.docs.core.util.format;

import com.google.common.io.Closer;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.util.PdfUtil;
import com.sismics.util.mime.MimeType;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Format handler for plain text, CSV, Markdown (.md), and HTML (.html) files.
 * Includes memory guardrails and Pan-CJK font rendering for thumbnails.
 *
 * @author bgamard
 */
public class TextPlainFormatHandler implements FormatHandler {
    /**
     * Max bytes to read when generating a 1st-page thumbnail (4 KB is plenty for page 1).
     */
    private static final int THUMBNAIL_MAX_BYTES = 4 * 1024;

    /**
     * Max bytes to read when extracting full-text search content (512 KB).
     */
    private static final int EXTRACT_MAX_BYTES = 512 * 1024;

    @Override
    public boolean accept(String mimeType) {
        return MimeType.TEXT_CSV.equals(mimeType)
                || MimeType.TEXT_PLAIN.equals(mimeType)
                || MimeType.TEXT_MARKDOWN.equals(mimeType)
                || MimeType.TEXT_HTML.equals(mimeType)
                || "text/x-markdown".equals(mimeType);
    }

    /**
     * Reads up to maxBytes from a file as a valid UTF-8 string without loading the entire file into memory.
     */
    private String readBoundedUtf8(Path file, int maxBytes) throws Exception {
        long fileSize = Files.size(file);
        int toRead = (int) Math.min(fileSize, maxBytes);
        if (toRead <= 0) {
            return "";
        }
        byte[] buf = new byte[toRead];
        int totalRead = 0;
        try (InputStream is = Files.newInputStream(file)) {
            while (totalRead < toRead) {
                int r = is.read(buf, totalRead, toRead - totalRead);
                if (r < 0) {
                    break;
                }
                totalRead += r;
            }
        }
        // Trim incomplete trailing UTF-8 multibyte sequence if truncated
        int validLen = totalRead;
        if (totalRead == maxBytes && fileSize > maxBytes) {
            while (validLen > 0 && (buf[validLen - 1] & 0xC0) == 0x80) {
                validLen--;
            }
            if (validLen > 0 && (buf[validLen - 1] & 0x80) != 0) {
                validLen--;
            }
        }
        return new String(buf, 0, validLen, StandardCharsets.UTF_8);
    }

    @Override
    public BufferedImage generateThumbnail(Path file) throws Exception {
        Document output = new Document(PageSize.A4, 40, 40, 40, 40);
        Path tempFile = AppContext.getInstance().getFileService().createTemporaryFile();
        try (OutputStream pdfOutputStream = Files.newOutputStream(tempFile)) {
            PdfWriter.getInstance(output, pdfOutputStream);
            output.open();

            String content = readBoundedUtf8(file, THUMBNAIL_MAX_BYTES);
            if (content.contains("<html") || content.contains("<body") || content.contains("<div") || content.contains("<p>")) {
                String parsed = Jsoup.parse(content).text();
                if (!parsed.trim().isEmpty()) {
                    content = parsed;
                }
            }
            if (content.trim().isEmpty()) {
                content = " ";
            }

            Font font = PdfUtil.getFontProvider().getFont(
                    PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 11f, Font.NORMAL, Color.BLACK);
            Paragraph paragraph = new Paragraph(content, font);
            paragraph.setAlignment(Element.ALIGN_LEFT);
            output.add(paragraph);
            output.close();
        }

        // Use the PDF format handler to render page 1
        return new PdfFormatHandler().generateThumbnail(tempFile);
    }

    @Override
    public String extractContent(String language, Path file) throws Exception {
        String content = readBoundedUtf8(file, EXTRACT_MAX_BYTES);
        if (content.contains("<html") || content.contains("<body") || content.contains("<!DOCTYPE")) {
            return Jsoup.parse(content).text();
        }
        return content;
    }

    @Override
    public void appendToPdf(Path file, PDDocument doc, boolean fitImageToPage, int margin, MemoryUsageSetting memUsageSettings, Closer closer) {
        // Text/Markdown/HTML files use native in-modal reader preview
    }
}
