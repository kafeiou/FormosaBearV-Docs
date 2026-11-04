package com.sismics.docs.core.util.format;

import com.google.common.io.Closer;
import com.sismics.docs.core.util.PdfUtil;
import com.sismics.util.mime.MimeType;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.common.usermodel.fonts.FontGroup;
import org.apache.poi.common.usermodel.fonts.FontInfo;
import org.apache.poi.sl.draw.DrawFontManager;
import org.apache.poi.sl.draw.DrawFontManagerDefault;
import org.apache.poi.sl.draw.Drawable;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PPTX format handler.
 *
 * @author bgamard
 */
public class PptxFormatHandler implements FormatHandler {
    /**
     * Cached PPTX loaded file.
     */
    private XMLSlideShow slideShow;

    @Override
    public boolean accept(String mimeType) {
        return MimeType.OFFICE_PRESENTATION.equals(mimeType);
    }

    @Override
    public BufferedImage generateThumbnail(Path file) throws Exception {
        XMLSlideShow pptx = loadPPtxFile(file);
        if (pptx.getSlides().size() > 0) {
            return generateImageFromSlide(pptx, 0);
        }

        return null;
    }

    @Override
    public String extractContent(String language, Path file) throws Exception {
        XMLSlideShow pptx = loadPPtxFile(file);
        return new SlideShowExtractor<>(pptx).getText();
    }

    @Override
    public void appendToPdf(Path file, PDDocument doc, boolean fitImageToPage, int margin, MemoryUsageSetting memUsageSettings, Closer closer) throws Exception {
        XMLSlideShow pptx = loadPPtxFile(file);
        List<XSLFSlide> slides = pptx.getSlides();
        Dimension pgsize = pptx.getPageSize();
        for (int slideIndex = 0; slideIndex < slides.size(); slideIndex++) {
            // One PDF page per slide
            PDPage page = new PDPage(new PDRectangle(pgsize.width, pgsize.height));
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                BufferedImage bim = generateImageFromSlide(pptx, slideIndex);
                PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bim);
                contentStream.drawImage(pdImage, 0, page.getMediaBox().getHeight() - pdImage.getHeight());
            }
            doc.addPage(page);
        }
    }

    private XMLSlideShow loadPPtxFile(Path file) throws Exception {
        if (slideShow == null) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                slideShow = new XMLSlideShow(inputStream);
            }
        }
        return slideShow;
    }

    /**
     * Generate an image from a PPTX slide.
     *
     * @param pptx PPTX
     * @param slideIndex Slide index
     * @return Image
     */
    private BufferedImage generateImageFromSlide(XMLSlideShow pptx, int slideIndex) {
        PdfUtil.registerFonts();
        XSLFSlide slide = pptx.getSlides().get(slideIndex);
        applyFallbackFontToShapes(slide.getShapes());

        Dimension pgsize = pptx.getPageSize();
        BufferedImage img = new BufferedImage(pgsize.width, pgsize.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = img.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(Drawable.FONT_HANDLER, new CjkDrawFontManager());

            graphics.setPaint(Color.white);
            graphics.fill(new Rectangle2D.Float(0, 0, pgsize.width, pgsize.height));
            slide.draw(graphics);
        } finally {
            graphics.dispose();
        }
        return img;
    }

    /**
     * Walk all shapes on a slide and assign the bundled Pan-CJK/Vietnamese font
     * to any text run whose configured font cannot render all characters in that run.
     */
    private void applyFallbackFontToShapes(List<XSLFShape> shapes) {
        if (shapes == null) {
            return;
        }
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFGroupShape) {
                applyFallbackFontToShapes(((XSLFGroupShape) shape).getShapes());
            } else if (shape instanceof XSLFTable) {
                XSLFTable table = (XSLFTable) shape;
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        applyFallbackFontToParagraphs(cell.getTextParagraphs());
                    }
                }
            } else if (shape instanceof XSLFTextShape) {
                applyFallbackFontToParagraphs(((XSLFTextShape) shape).getTextParagraphs());
            }
        }
    }

    private void applyFallbackFontToParagraphs(List<XSLFTextParagraph> paragraphs) {
        if (paragraphs == null) {
            return;
        }
        for (XSLFTextParagraph paragraph : paragraphs) {
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                String text = run.getRawText();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                String family = run.getFontFamily();
                boolean needsFallback = true;
                if (family != null && !family.isEmpty()) {
                    Font testFont = new Font(family, Font.PLAIN, 12);
                    if (!"Dialog".equalsIgnoreCase(testFont.getFamily()) && testFont.canDisplayUpTo(text) == -1) {
                        needsFallback = false;
                    }
                }
                if (needsFallback) {
                    run.setFontFamily(PdfUtil.BUNDLED_CJK_FONT_FAMILY, FontGroup.LATIN);
                    run.setFontFamily(PdfUtil.BUNDLED_CJK_FONT_FAMILY, FontGroup.EAST_ASIAN);
                    run.setFontFamily(PdfUtil.BUNDLED_CJK_FONT_FAMILY, FontGroup.COMPLEX_SCRIPT);
                }
            }
        }
    }

    /**
     * Custom DrawFontManager that falls back to the bundled Pan-CJK font.
     */
    private static class CjkDrawFontManager extends DrawFontManagerDefault implements DrawFontManager {
        private static final FontInfo CJK_FONT_INFO = new FontInfo() {
            @Override
            public String getTypeface() {
                return PdfUtil.BUNDLED_CJK_FONT_FAMILY;
            }
        };

        @Override
        public FontInfo getFallbackFont(Graphics2D graphics, FontInfo fontInfo) {
            return CJK_FONT_INFO;
        }

        @Override
        public Font createAWTFont(Graphics2D graphics, FontInfo fontInfo, double fontSize, boolean bold, boolean italic) {
            int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
            String typeface = fontInfo != null ? fontInfo.getTypeface() : PdfUtil.BUNDLED_CJK_FONT_FAMILY;
            Font font = new Font(typeface, style, 12);
            if ("Dialog".equalsIgnoreCase(font.getFamily())) {
                font = new Font(PdfUtil.BUNDLED_CJK_FONT_FAMILY, style, 12);
            }
            return font.deriveFont((float) fontSize);
        }
    }
}
