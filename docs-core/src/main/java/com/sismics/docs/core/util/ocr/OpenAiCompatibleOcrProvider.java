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
 * OpenAI-compatible Vision OCR provider (/v1/chat/completions and /v1/models).
 * Works with vLLM, LM Studio, llama.cpp server, OpenRouter, OpenAI, Gemini OpenAI-compat, etc.
 */
public class OpenAiCompatibleOcrProvider extends AbstractHttpVisionOcrProvider {
    public static final String ID = "openai";

    @Override
    public String getId() {
        return ID;
    }

    private static String buildV1Url(String rawBaseUrl, String pathSuffix) {
        String base = normalizeBaseUrl(rawBaseUrl);
        if (base.endsWith("/v1")) {
            return base + pathSuffix;
        }
        return base + "/v1" + pathSuffix;
    }

    @Override
    public String extractText(String language, BufferedImage image, ConfigUtil.OcrConfig config) throws Exception {
        if (Strings.isNullOrEmpty(config.getAiModel())) {
            throw new IllegalStateException("OpenAI-compatible AI model name is not configured");
        }

        String base64Png = encodeImageAsPngBase64(image);
        String prompt = Strings.isNullOrEmpty(config.getAiPrompt())
                ? ConfigUtil.DEFAULT_OCR_AI_PROMPT
                : config.getAiPrompt();

        JsonObject payload = Json.createObjectBuilder()
                .add("model", config.getAiModel().trim())
                .add("temperature", 0)
                .add("messages", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("role", "system")
                                .add("content", SYSTEM_OCR_GUARDRAIL))
                        .add(Json.createObjectBuilder()
                                .add("role", "user")
                                .add("content", Json.createArrayBuilder()
                                        .add(Json.createObjectBuilder()
                                                .add("type", "text")
                                                .add("text", prompt))
                                        .add(Json.createObjectBuilder()
                                                .add("type", "image_url")
                                                .add("image_url", Json.createObjectBuilder()
                                                        .add("url", "data:image/png;base64," + base64Png))))))
                .build();

        JsonObject response = sendJsonRequest(
                buildV1Url(config.getAiBaseUrl(), "/chat/completions"),
                config.getAiApiKey(),
                payload.toString(),
                config.getAiTimeoutSeconds());

        if (response.containsKey("choices") && response.get("choices") instanceof JsonArray) {
            JsonArray choices = response.getJsonArray("choices");
            if (!choices.isEmpty()) {
                JsonObject message = choices.getJsonObject(0).getJsonObject("message");
                if (message != null) {
                    return message.getString("content", "").trim();
                }
            }
        }
        return "";
    }

    @Override
    public List<String> listModels(ConfigUtil.OcrConfig config) throws Exception {
        JsonObject response = sendJsonRequest(
                buildV1Url(config.getAiBaseUrl(), "/models"),
                config.getAiApiKey(),
                null,
                Math.min(30, config.getAiTimeoutSeconds()));

        List<String> models = new ArrayList<>();
        if (response.containsKey("data") && response.get("data") instanceof JsonArray) {
            JsonArray arr = response.getJsonArray("data");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject item = arr.getJsonObject(i);
                String id = item.getString("id", null);
                if (!Strings.isNullOrEmpty(id)) {
                    models.add(id);
                }
            }
        }
        return models;
    }
}
