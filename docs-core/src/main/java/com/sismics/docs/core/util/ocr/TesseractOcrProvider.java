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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
     * Result of checking local Tesseract executable availability.
     */
    public static final class TesseractCheckResult {
        private final boolean ok;
        private final String resolvedPath;
        private final String version;
        private final List<String> languages;
        private final String error;

        public TesseractCheckResult(boolean ok, String resolvedPath, String version, List<String> languages, String error) {
            this.ok = ok;
            this.resolvedPath = Strings.nullToEmpty(resolvedPath);
            this.version = Strings.nullToEmpty(version);
            this.languages = languages == null ? Collections.emptyList() : Collections.unmodifiableList(languages);
            this.error = Strings.nullToEmpty(error);
        }

        public boolean isOk() { return ok; }
        public String getResolvedPath() { return resolvedPath; }
        public String getVersion() { return version; }
        public List<String> getLanguages() { return languages; }
        public String getError() { return error; }
    }

    /**
     * Verifies whether the configured (or auto-detected) Tesseract executable can be launched,
     * returning its version and installed language packs.
     */
    public static TesseractCheckResult checkAvailability(String configuredPath) {
        String resolvedCmd = resolveTesseractCommand(configuredPath);
        try {
            ProcessBuilder versionPb = new ProcessBuilder(resolvedCmd, "--version");
            versionPb.redirectErrorStream(true);
            Process versionProc = versionPb.start();
            String versionOutput;
            try (InputStream is = versionProc.getInputStream()) {
                versionOutput = CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
            if (!versionProc.waitFor(10, TimeUnit.SECONDS)) {
                versionProc.destroyForcibly();
                return new TesseractCheckResult(false, resolvedCmd, "", Collections.emptyList(),
                        "Timed out while executing Tesseract (--version)");
            }
            if (versionProc.exitValue() != 0) {
                return new TesseractCheckResult(false, resolvedCmd, "", Collections.emptyList(),
                        "Tesseract exited with code " + versionProc.exitValue() + ": " + firstLine(versionOutput));
            }
            String version = firstLine(versionOutput);

            List<String> languages = new ArrayList<>();
            try {
                ProcessBuilder langsPb = new ProcessBuilder(resolvedCmd, "--list-langs");
                langsPb.redirectErrorStream(true);
                Process langsProc = langsPb.start();
                List<String> lines = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(langsProc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line.trim());
                    }
                }
                if (langsProc.waitFor(10, TimeUnit.SECONDS) && langsProc.exitValue() == 0) {
                    for (String line : lines) {
                        if (line.isEmpty() || line.toLowerCase(Locale.ROOT).startsWith("list of available languages")) {
                            continue;
                        }
                        languages.add(line);
                    }
                    Collections.sort(languages);
                } else {
                    langsProc.destroyForcibly();
                }
            } catch (Exception e) {
                log.debug("Unable to list Tesseract languages for '{}': {}", resolvedCmd, e.getMessage());
            }

            return new TesseractCheckResult(true, resolvedCmd, version, languages, "");
        } catch (IOException e) {
            return new TesseractCheckResult(false, resolvedCmd, "", Collections.emptyList(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TesseractCheckResult(false, resolvedCmd, "", Collections.emptyList(), "Interrupted");
        }
    }

    private static String firstLine(String text) {
        if (Strings.isNullOrEmpty(text)) {
            return "";
        }
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    /**
     * Resolves the Tesseract executable path from config, PATH, or common Windows installation paths.
     * Supports stripping surrounding quotes (e.g. from Windows Explorer "Copy as path") and
     * automatically appending tesseract.exe / tesseract when a directory path is provided.
     */
    public static String resolveTesseractCommand(String configuredPath) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return resolveTesseractCommand(configuredPath, isWindows);
    }

    static String resolveTesseractCommand(String configuredPath, boolean isWindows) {
        if (!Strings.isNullOrEmpty(configuredPath)) {
            String cleaned = stripQuotes(configuredPath.trim());
            if (!cleaned.isEmpty()) {
                try {
                    Path p = Paths.get(cleaned);
                    if (Files.isDirectory(p)) {
                        Path exeInDir = p.resolve(isWindows ? "tesseract.exe" : "tesseract");
                        if (Files.isRegularFile(exeInDir)) {
                            return exeInDir.toString();
                        }
                        Path fallbackInDir = p.resolve("tesseract");
                        if (Files.isRegularFile(fallbackInDir)) {
                            return fallbackInDir.toString();
                        }
                        return exeInDir.toString();
                    }
                } catch (Exception ignored) {
                    // Fall through to return cleaned string as-is
                }
                return cleaned;
            }
        }
        if (isWindows) {
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

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
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
