package com.sismics.docs.core.util.ocr;

import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.format.PdfFormatHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for extensible OCR providers, HTTP Vision protocols, and PDF text usability checks.
 */
public class TestOcrService {

    private BufferedImage createTestImage() {
        BufferedImage img = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 120, 40);
        g.setColor(Color.BLACK);
        g.drawString("Test 123", 10, 25);
        g.dispose();
        return img;
    }

    @Test
    public void testOllamaProviderExtractAndListModels() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = "{\"models\":[{\"name\":\"qwen2.5vl:7b\"},{\"name\":\"minicpm-v:latest\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/api/chat", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Assert.assertTrue(reqBody.contains("\"model\":\"qwen2.5vl:7b\""));
            Assert.assertTrue(reqBody.contains("\"images\":["));
            byte[] body = "{\"message\":{\"role\":\"assistant\",\"content\":\"台灣銀行 測試文件 123\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ConfigUtil.OcrConfig config = new ConfigUtil.OcrConfig(
                    true, "ollama", false, baseUrl, "", "qwen2.5vl:7b", 30,
                    ConfigUtil.DEFAULT_OCR_AI_PROMPT, false, "");

            List<String> models = OcrService.listModels(config);
            Assert.assertEquals(2, models.size());
            Assert.assertEquals("qwen2.5vl:7b", models.get(0));

            String text = OcrService.extractText("chi_tra", createTestImage(), config);
            Assert.assertEquals("台灣銀行 測試文件 123", text);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testOpenAiCompatibleProviderWithBearerAndV1Base() throws Exception {
        AtomicBoolean authHeaderSeen = new AtomicBoolean(false);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authHeaderSeen.set("Bearer sk-test-123".equals(exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] body = "{\"data\":[{\"id\":\"gpt-4o-mini\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/v1/chat/completions", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Assert.assertTrue(reqBody.contains("data:image/png;base64,"));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"OpenAI Vision Result\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            String baseUrlWithV1 = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            ConfigUtil.OcrConfig config = new ConfigUtil.OcrConfig(
                    true, "openai", false, baseUrlWithV1, "sk-test-123", "gpt-4o-mini", 30,
                    ConfigUtil.DEFAULT_OCR_AI_PROMPT, false, "");

            List<String> models = OcrService.listModels(config);
            Assert.assertTrue(authHeaderSeen.get());
            Assert.assertEquals(1, models.size());
            Assert.assertEquals("gpt-4o-mini", models.get(0));

            String text = OcrService.extractText("eng", createTestImage(), config);
            Assert.assertEquals("OpenAI Vision Result", text);
        } finally {
            server.stop(0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsNonHttpBaseUrl() {
        AbstractHttpVisionOcrProvider.normalizeBaseUrl("file:///etc/passwd");
    }

    @Test
    public void testPdfPageTextUsabilityChecks() throws Exception {
        java.lang.reflect.Method method = PdfFormatHandler.class.getDeclaredMethod("isPageTextUsable", String.class);
        method.setAccessible(true);

        Assert.assertFalse((Boolean) method.invoke(null, (Object) null));
        Assert.assertFalse((Boolean) method.invoke(null, "   \n  "));
        Assert.assertFalse((Boolean) method.invoke(null, "- 1 -"));
        Assert.assertFalse((Boolean) method.invoke(null, "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD"));
        Assert.assertTrue((Boolean) method.invoke(null, "這是一份經過 Adobe Acrobat 辨識的中文合約文件內容。"));
    }
}
