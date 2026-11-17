package com.sismics.docs.core.util.format;

import com.google.common.io.Closer;
import com.sismics.docs.core.util.FileUtil;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.util.mime.MimeType;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF format handler.
 *
 * @author bgamard
 */
public class PdfFormatHandler implements FormatHandler {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(PdfFormatHandler.class);

    /**
     * Max memory in bytes (50 MB) before PDFBox spills to temporary disk files.
     */
    private static final long MAX_MAIN_MEMORY_BYTES = 50L * 1024L * 1024L;

    /**
     * Max pages to extract text from for search indexing.
     */
    private static final int MAX_TEXT_EXTRACT_PAGES = 500;

    /**
     * Max pages to OCR on image-only PDFs to prevent CPU/memory exhaustion.
     */
    private static final int MAX_OCR_PAGES = 50;

    @Override
    public boolean accept(String mimeType) {
        return mimeType.equals(MimeType.APPLICATION_PDF);
    }

    @Override
    public BufferedImage generateThumbnail(Path file) throws Exception {
        try (PDDocument pdfDocument = PDDocument.load(file.toFile(), MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES))) {
            PDFRenderer renderer = new PDFRenderer(pdfDocument);
            return renderer.renderImage(0);
        }
    }

    @Override
    public String extractContent(String language, Path file) {
        ConfigUtil.OcrConfig ocrConfig = ConfigUtil.getOcrConfig();
        boolean ocrEnabled = ocrConfig.isEnabled();
        boolean forceAiOcr = ocrEnabled && ocrConfig.isPdfForceAi() && !"tesseract".equals(ocrConfig.getEngine());

        StringBuilder sb = new StringBuilder();
        try (PDDocument pdfDocument = PDDocument.load(file.toFile(), MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES))) {
            int totalPages = Math.min(pdfDocument.getNumberOfPages(), MAX_TEXT_EXTRACT_PAGES);
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = null;
            int ocrPagesUsed = 0;

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                String pageText = "";
                if (!forceAiOcr) {
                    stripper.setStartPage(pageNum);
                    stripper.setEndPage(pageNum);
                    pageText = stripper.getText(pdfDocument);
                }

                boolean needsOcr = ocrEnabled && (forceAiOcr || !isPageTextUsable(pageText));
                if (needsOcr && ocrPagesUsed < MAX_OCR_PAGES) {
                    if (renderer == null) {
                        renderer = new PDFRenderer(pdfDocument);
                    }
                    ocrPagesUsed++;
                    log.info("OCR page {}/{} (pdf page {}/{}) using engine '{}'",
                            ocrPagesUsed, Math.min(totalPages, MAX_OCR_PAGES), pageNum, totalPages, ocrConfig.getEngine());
                    BufferedImage pageImage = renderer.renderImageWithDPI(pageNum - 1, 300, ImageType.RGB);
                    try {
                        String ocrText = FileUtil.ocrFile(language, pageImage);
                        if (ocrText != null && !ocrText.trim().isEmpty()) {
                            sb.append(' ').append(ocrText.trim());
                            continue;
                        }
                    } catch (Exception e) {
                        log.error("Error while OCR-izing PDF page " + pageNum, e);
                    } finally {
                        pageImage.flush();
                    }
                    // Last-resort fallback: if forced AI OCR failed/returned empty, try embedded PDF text
                    if (forceAiOcr && (pageText == null || pageText.isEmpty())) {
                        stripper.setStartPage(pageNum);
                        stripper.setEndPage(pageNum);
                        pageText = stripper.getText(pdfDocument);
                        if (pageText != null && !pageText.trim().isEmpty()) {
                            log.info("Recovered embedded text for PDF page {} after OCR fallback", pageNum);
                        }
                    }
                }

                if (pageText != null && !pageText.trim().isEmpty()) {
                    sb.append(' ').append(pageText.trim());
                }
            }
        } catch (Exception e) {
            log.error("Error while extracting text from the PDF", e);
        }

        return sb.toString().trim();
    }

    /**
     * Determines whether extracted embedded PDF page text is usable without OCR.
     * Rejects empty/whitespace-only text, minimal noise (like lone page numbers),
     * and garbled CMap replacement characters (\uFFFD).
     */
    static boolean isPageTextUsable(String pageText) {
        if (pageText == null) {
            return false;
        }
        String trimmed = pageText.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        int meaningfulChars = 0;
        int replacementChars = 0;
        int nonWhitespaceChars = 0;
        for (int i = 0; i < trimmed.length(); ) {
            int cp = trimmed.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                continue;
            }
            nonWhitespaceChars++;
            if (cp == '\uFFFD') {
                replacementChars++;
            } else if (Character.isLetterOrDigit(cp)) {
                meaningfulChars++;
            }
        }

        if (replacementChars >= 3 || (nonWhitespaceChars > 0 && (replacementChars * 100.0 / nonWhitespaceChars) > 5.0)) {
            return false;
        }
        return meaningfulChars >= 10;
    }

    @Override
    public void appendToPdf(Path file, PDDocument doc, boolean fitImageToPage, int margin, MemoryUsageSetting memUsageSettings, Closer closer) throws Exception {
        PDDocument mergeDoc = PDDocument.load(file.toFile(), memUsageSettings);
        closer.register(mergeDoc);
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.appendDocument(doc, mergeDoc);
    }
}
