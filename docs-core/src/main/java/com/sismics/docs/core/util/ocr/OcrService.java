package com.sismics.docs.core.util.ocr;

import com.google.common.base.Strings;
import com.sismics.docs.core.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central OCR orchestrator that dispatches to registered {@link OcrProvider}s
 * with optional automatic fallback to Tesseract.
 */
public class OcrService {
    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private static final Map<String, OcrProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        registerProvider(new TesseractOcrProvider());
        registerProvider(new OllamaOcrProvider());
        registerProvider(new OpenAiCompatibleOcrProvider());
    }

    public static void registerProvider(OcrProvider provider) {
        PROVIDERS.put(provider.getId(), provider);
    }

    public static OcrProvider getProvider(String id) {
        if (Strings.isNullOrEmpty(id)) {
            return PROVIDERS.get(TesseractOcrProvider.ID);
        }
        OcrProvider provider = PROVIDERS.get(id.trim().toLowerCase());
        return provider != null ? provider : PROVIDERS.get(TesseractOcrProvider.ID);
    }

    /**
     * Extracts text from an image using the configured OCR provider and fallback policy.
     */
    public static String extractText(String language, BufferedImage image) throws Exception {
        return extractText(language, image, ConfigUtil.getOcrConfig());
    }

    /**
     * Extracts text using an explicit OCR configuration snapshot (useful for testing).
     */
    public static String extractText(String language, BufferedImage image, ConfigUtil.OcrConfig config) throws Exception {
        OcrProvider primary = getProvider(config.getEngine());
        if (TesseractOcrProvider.ID.equals(primary.getId())) {
            return primary.extractText(language, image, config);
        }

        try {
            String result = primary.extractText(language, image, config);
            if (!Strings.isNullOrEmpty(result) && !result.trim().isEmpty()) {
                return result;
            }
            log.warn("OCR provider '{}' returned empty text", primary.getId());
        } catch (Exception e) {
            log.warn("OCR provider '{}' failed ({}): {}", primary.getId(), e.getClass().getSimpleName(), e.getMessage());
            if (!config.isFallbackTesseract()) {
                throw e;
            }
        }

        if (config.isFallbackTesseract()) {
            log.info("Falling back to Tesseract OCR after '{}' failure/empty result", primary.getId());
            return getProvider(TesseractOcrProvider.ID).extractText(language, image, config);
        }
        return "";
    }

    /**
     * Tests connectivity and fetches available models for the given AI OCR configuration.
     */
    public static List<String> listModels(ConfigUtil.OcrConfig config) throws Exception {
        OcrProvider provider = getProvider(config.getEngine());
        if (TesseractOcrProvider.ID.equals(provider.getId())) {
            return Collections.emptyList();
        }
        return provider.listModels(config);
    }
}
