package com.sismics.docs.core.util.ocr;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.sismics.util.ImageDeskew;
import com.sismics.util.Scalr;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.util.io.InputStreamReaderThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Local Tesseract CLI OCR provider.
 */
public class TesseractOcrProvider implements OcrProvider {
    private static final Logger log = LoggerFactory.getLogger(TesseractOcrProvider.class);

    public static final String ID = "tesseract";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String extractText(String language, BufferedImage image, ConfigUtil.OcrConfig config) throws Exception {
        String tesseractCmd = resolveTesseractCommand(config != null ? config.getTesseractPath() : null);

        // Upscale, grayscale and deskew the image (copy/work without destroying caller's source image if reused)
        BufferedImage resizedImage = Scalr.resize(image, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, 3500, Scalr.OP_ANTIALIAS, Scalr.OP_GRAYSCALE);
        ImageDeskew imageDeskew = new ImageDeskew(resizedImage);
        BufferedImage deskewedImage = Scalr.rotate(resizedImage, -imageDeskew.getSkewAngle(), Scalr.OP_ANTIALIAS, Scalr.OP_GRAYSCALE);
        resizedImage.flush();
        Path tmpFile = AppContext.getInstance().getFileService().createTemporaryFile();
        try {
            ImageIO.write(deskewedImage, "tiff", tmpFile.toFile());
            deskewedImage.flush();

            List<String> command = Lists.newLinkedList(Arrays.asList(
                    tesseractCmd, tmpFile.toAbsolutePath().toString(), "stdout", "-l",
                    Strings.isNullOrEmpty(language) ? "eng" : language));
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                log.warn("Tesseract executable '{}' could not be started: {}", tesseractCmd, e.getMessage());
                throw e;
            }

            new InputStreamReaderThread(process.getErrorStream(), tesseractCmd).start();
            try (InputStream is = process.getInputStream()) {
                return CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } finally {
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Resolves the Tesseract executable path from config, PATH, or common Windows installation paths.
     */
    static String resolveTesseractCommand(String configuredPath) {
        if (!Strings.isNullOrEmpty(configuredPath)) {
            return configuredPath.trim();
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // Check if tesseract is on PATH first; if not, probe standard Windows install directories
            if (!isCommandOnPath("tesseract")) {
                List<String> candidates = Arrays.asList(
                        "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                        "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe"
                );
                for (String candidate : candidates) {
                    if (Files.isRegularFile(Paths.get(candidate))) {
                        return candidate;
                    }
                }
            }
        }
        return "tesseract";
    }

    private static boolean isCommandOnPath(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (Strings.isNullOrEmpty(pathEnv)) {
            return false;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (Files.isRegularFile(Paths.get(dir, cmd)) || Files.isRegularFile(Paths.get(dir, cmd + ".exe"))) {
                return true;
            }
        }
        return false;
    }
}
