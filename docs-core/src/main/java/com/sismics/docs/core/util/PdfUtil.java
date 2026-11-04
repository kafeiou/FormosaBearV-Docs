package com.sismics.docs.core.util;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.Resources;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.dto.DocumentDto;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.util.format.FormatHandler;
import com.sismics.docs.core.util.format.FormatHandlerUtil;
import com.sismics.docs.core.util.pdf.PdfPage;
import fr.opensagres.xdocreport.itext.extension.font.IFontProvider;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.DocsPDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * PDF utilities.
 * 
 * @author bgamard
 */
public class PdfUtil {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(PdfUtil.class);

    /**
     * Bundled Pan-CJK + Vietnamese + Latin font family name.
     */
    public static final String BUNDLED_CJK_FONT_FAMILY = "Noto Sans CJK TC";

    /**
     * Flag to ensure fonts are registered only once.
     */
    private static volatile boolean fontsRegistered = false;

    /**
     * Convert a document and its files to a merged PDF file.
     * 
     * @param documentDto Document DTO
     * @param fileList List of files
     * @param fitImageToPage Fit images to the page
     * @param metadata Add a page with metadata
     * @param margin Margins in millimeters
     * @param outputStream Output stream to write to, will be closed
     */
    public static void convertToPdf(DocumentDto documentDto, List<File> fileList,
            boolean fitImageToPage, boolean metadata, int margin, OutputStream outputStream) throws Exception {
        // Setup PDFBox
        Closer closer = Closer.create();
        MemoryUsageSetting memUsageSettings = MemoryUsageSetting.setupMixed(1000000); // 1MB max memory usage
        memUsageSettings.setTempDir(new java.io.File(System.getProperty("java.io.tmpdir"))); // To OS temp

        // Create a blank PDF
        try (PDDocument doc = new PDDocument(memUsageSettings)) {
            // Add metadata
            if (metadata) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PdfPage pdfPage = new PdfPage(doc, page, margin * Constants.MM_PER_INCH, DocsPDType1Font.HELVETICA, 12)) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    pdfPage.addText(documentDto.getTitle(), true, DocsPDType1Font.HELVETICA_BOLD, 16)
                        .newLine()
                        .addText("Created by " + documentDto.getCreator()
                            + " on " + dateFormat.format(new Date(documentDto.getCreateTimestamp())), true)
                        .newLine()
                        .addText(documentDto.getDescription())
                        .newLine();
                    if (!Strings.isNullOrEmpty(documentDto.getSubject())) {
                        pdfPage.addText("Subject: " + documentDto.getSubject());
                    }
                    if (!Strings.isNullOrEmpty(documentDto.getIdentifier())) {
                        pdfPage.addText("Identifier: " + documentDto.getIdentifier());
                    }
                    if (!Strings.isNullOrEmpty(documentDto.getPublisher())) {
                        pdfPage.addText("Publisher: " + documentDto.getPublisher());
                    }
                    if (!Strings.isNullOrEmpty(documentDto.getFormat())) {
                        pdfPage.addText("Format: " + documentDto.getFormat());
                    }
                    if (!Strings.isNullOrEmpty(documentDto.getSource())) {
                        pdfPage.addText("Source: " + documentDto.getSource());
                    }
                    if (!Strings.isNullOrEmpty(documentDto.getType())) {
                        pdfPage.addText("Type: " + documentDto.getType());
                    }
                    if (!Strings.isNullOrEmpty(documentDto.getCoverage())) {
                        pdfPage.addText("Coverage: " + documentDto.getCoverage());
                    }
                    if (!Strings.isNullOrEmpty(documentDto.getRights())) {
                        pdfPage.addText("Rights: " + documentDto.getRights());
                    }
                    pdfPage.addText("Language: " + documentDto.getLanguage())
                        .newLine()
                        .addText("Files in this document : " + fileList.size(), false, DocsPDType1Font.HELVETICA_BOLD, 12);
                }
            }
            
            // Add files
            for (File file : fileList) {
                Path storedFile = DirectoryUtil.getStorageDirectory(file).resolve(file.getName());

                // Decrypt the file to a temporary file
                Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, file.getPrivateKey());
                FormatHandler formatHandler = FormatHandlerUtil.find(file.getMimeType());
                if (formatHandler != null) {
                    formatHandler.appendToPdf(unencryptedFile, doc, fitImageToPage, margin, memUsageSettings, closer);
                }
            }
            
            doc.save(outputStream); // Write to the output stream
            closer.close(); // Close all remaining opened PDF
        }
    }

    /**
     * Register bundled and system fonts for Java 2D (AWT) and iText PDF generation.
     */
    public static synchronized void registerFonts() {
        if (fontsRegistered) {
            return;
        }
        try {
            registerBundledFont("fonts/LiberationMono-Regular.ttf", "sismics_docs_font_mono", ".ttf", "LiberationMono-Regular");
            registerBundledFont("fonts/NotoSansCJKtc-Regular.otf", "teedy_ng_font_cjk", ".otf", BUNDLED_CJK_FONT_FAMILY);
            FontFactory.registerDirectories();
            fontsRegistered = true;
        } catch (Exception e) {
            log.error("Error loading bundled fonts", e);
        }
    }

    private static void registerBundledFont(String resourcePath, String tempPrefix, String tempSuffix, String alias) throws Exception {
        URL url = Resources.getResource(resourcePath);
        try (InputStream is = url.openStream()) {
            Path file = Files.createTempFile(tempPrefix, tempSuffix);
            file.toFile().deleteOnExit();
            try (OutputStream os = Files.newOutputStream(file)) {
                ByteStreams.copy(is, os);
            }
            Font awtFont = Font.createFont(Font.TRUETYPE_FONT, file.toFile());
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(awtFont);
            FontFactory.register(file.toAbsolutePath().toString(), alias);
        }
    }

    /**
     * Returns an iText font provider that falls back to the bundled Pan-CJK font.
     *
     * @return IFontProvider
     */
    public static IFontProvider getFontProvider() {
        registerFonts();
        return (String familyName, String encoding, float size, int style, Color color) -> {
            try {
                com.lowagie.text.Font font = FontFactory.getFont(familyName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, size, style, color);
                if (font != null && font.getBaseFont() != null) {
                    return font;
                }
            } catch (Exception e) {
                // Ignore and fall back to bundled CJK font
            }
            return FontFactory.getFont(BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, size, style, color);
        };
    }
}
