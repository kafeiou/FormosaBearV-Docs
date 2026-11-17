package com.sismics.docs.core.util.ocr;

import com.google.common.base.Strings;
import com.sismics.docs.core.util.ConfigUtil;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Ollama native Vision OCR provider (/api/chat and /api/tags).
 */
public class OllamaOcrProvider extends AbstractHttpVisionOcrProvider {
    public static final String ID = "ollama";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String extractText(String language, BufferedImage image, ConfigUtil.OcrConfig config) throws Exception {
        String baseUrl = normalizeBaseUrl(config.getAiBaseUrl());
        if (Strings.isNullOrEmpty(config.getAiModel())) {
            throw new IllegalStateException("Ollama AI model name is not configured");
        }

        String base64Png = encodeImageAsPngBase64(image);
        String prompt = Strings.isNullOrEmpty(config.getAiPrompt())
                ? ConfigUtil.DEFAULT_OCR_AI_PROMPT
                : config.getAiPrompt();

        JsonObject payload = Json.createObjectBuilder()
                .add("model", config.getAiModel().trim())
                .add("stream", false)
                .add("options", Json.createObjectBuilder()
                        .add("temperature", 0))
                .add("messages", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("role", "user")
                                .add("content", prompt)
                                .add("images", Json.createArrayBuilder().add(base64Png))))
                .build();

        JsonObject response = sendJsonRequest(
                baseUrl + "/api/chat",
                config.getAiApiKey(),
                payload.toString(),
                config.getAiTimeoutSeconds());

        if (response.containsKey("message") && response.get("message") instanceof JsonObject) {
            return response.getJsonObject("message").getString("content", "").trim();
        }
        return response.getString("response", "").trim();
    }

    @Override
    public List<String> listModels(ConfigUtil.OcrConfig config) throws Exception {
        String baseUrl = normalizeBaseUrl(config.getAiBaseUrl());
        JsonObject response = sendJsonRequest(
                baseUrl + "/api/tags",
                config.getAiApiKey(),
                null,
                Math.min(30, config.getAiTimeoutSeconds()));

        List<String> models = new ArrayList<>();
        if (response.containsKey("models") && response.get("models") instanceof JsonArray) {
            JsonArray arr = response.getJsonArray("models");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject item = arr.getJsonObject(i);
                String name = item.getString("name", null);
                if (!Strings.isNullOrEmpty(name)) {
                    models.add(name);
                }
            }
        }
        return models;
    }
}
