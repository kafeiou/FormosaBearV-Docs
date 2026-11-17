package com.sismics.docs.core.util.ocr;

import com.sismics.docs.core.util.ConfigUtil;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * Extensible OCR provider interface.
 * Implement this interface and register in {@link OcrService} to add new OCR / AI vision backends.
 */
public interface OcrProvider {
    /**
     * Unique provider identifier (e.g. "tesseract", "ollama", "openai").
     */
    String getId();

    /**
     * Extracts text from the given image.
     *
     * @param language Document language (3-letter code, e.g. "chi_tra", "eng")
     * @param image    Image to recognize
     * @param config   Current OCR configuration snapshot
     * @return Recognized plain text
     * @throws Exception if recognition fails
     */
    String extractText(String language, BufferedImage image, ConfigUtil.OcrConfig config) throws Exception;

    /**
     * Lists available models from this provider, if applicable.
     *
     * @param config OCR configuration snapshot
     * @return List of model identifiers
     * @throws Exception if querying models fails
     */
    default List<String> listModels(ConfigUtil.OcrConfig config) throws Exception {
        return Collections.emptyList();
    }
}
