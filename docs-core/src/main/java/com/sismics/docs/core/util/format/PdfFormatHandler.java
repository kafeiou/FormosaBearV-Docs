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
        String content = null;
        try (PDDocument pdfDocument = PDDocument.load(file.toFile(), MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(pdfDocument.getNumberOfPages(), MAX_TEXT_EXTRACT_PAGES));
            content = stripper.getText(pdfDocument);
        } catch (Exception e) {
            log.error("Error while extracting text from the PDF", e);
        }

        // No text content, try to OCR it (bounded by MAX_OCR_PAGES)
        if (language != null && content != null && content.trim().isEmpty() && ConfigUtil.getConfigBooleanValue(ConfigType.OCR_ENABLED, true)) {
            StringBuilder sb = new StringBuilder();
            try (PDDocument pdfDocument = PDDocument.load(file.toFile(), MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES))) {
                PDFRenderer renderer = new PDFRenderer(pdfDocument);
                int pagesToOcr = Math.min(pdfDocument.getNumberOfPages(), MAX_OCR_PAGES);
                for (int pageIndex = 0; pageIndex < pagesToOcr; pageIndex++) {
                    log.info("OCR page " + (pageIndex + 1) + "/" + pagesToOcr + " of PDF file containing only images");
                    sb.append(" ");
                    sb.append(FileUtil.ocrFile(language, renderer.renderImageWithDPI(pageIndex, 300, ImageType.GRAY)));
                }
                return sb.toString();
            } catch (Exception e) {
                log.error("Error while OCR-izing the PDF", e);
            }
        }

        return content;
    }

    @Override
    public void appendToPdf(Path file, PDDocument doc, boolean fitImageToPage, int margin, MemoryUsageSetting memUsageSettings, Closer closer) throws Exception {
        PDDocument mergeDoc = PDDocument.load(file.toFile(), memUsageSettings);
        closer.register(mergeDoc);
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.appendDocument(doc, mergeDoc);
    }
}
