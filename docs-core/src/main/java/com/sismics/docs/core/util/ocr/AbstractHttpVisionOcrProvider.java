package com.sismics.docs.core.util.ocr;

import com.google.common.base.Strings;
import com.sismics.util.Scalr;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Shared HTTP + PNG Base64 + JSON helper for vision-based OCR providers.
 */
public abstract class AbstractHttpVisionOcrProvider implements OcrProvider {
    private static final int MAX_VISION_IMAGE_DIMENSION = 1536;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Validates and normalizes an HTTP/HTTPS base URL.
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (Strings.isNullOrEmpty(baseUrl)) {
            throw new IllegalArgumentException("AI OCR base URL must not be empty");
        }
        String trimmed = baseUrl.trim().replaceAll("/+$", "");
        URI uri = URI.create(trimmed);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http:// and https:// URLs are supported for AI OCR");
        }
        if (Strings.isNullOrEmpty(uri.getHost())) {
            throw new IllegalArgumentException("AI OCR base URL must include a valid host");
        }
        return trimmed;
    }

    /**
     * Scales large images down to at most 2048px on the longest edge and encodes as PNG Base64.
     */
    protected static String encodeImageAsPngBase64(BufferedImage image) throws IOException {
        BufferedImage toEncode = image;
        boolean resized = false;
        int maxDim = Math.max(image.getWidth(), image.getHeight());
        if (maxDim > MAX_VISION_IMAGE_DIMENSION) {
            toEncode = Scalr.resize(image, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, MAX_VISION_IMAGE_DIMENSION);
            resized = true;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(toEncode, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } finally {
            if (resized) {
                toEncode.flush();
            }
        }
    }

    private static URI validateOutboundUriForOcr(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid OCR URL: " + e.getMessage(), e);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("Only HTTP/HTTPS OCR URLs are allowed");
        }
        if (Strings.isNullOrEmpty(uri.getHost())) {
            throw new IOException("OCR URL must include a host");
        }
        if (!Strings.isNullOrEmpty(uri.getUserInfo())) {
            throw new IOException("OCR URL must not contain user info");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses == null || addresses.length == 0) {
                throw new IOException("OCR host did not resolve");
            }
            for (InetAddress address : addresses) {
                if (isForbiddenAddress(address)) {
                    throw new IOException("OCR URL points to a forbidden address");
                }
            }
        } catch (UnknownHostException e) {
            throw new IOException("OCR host is not resolvable", e);
        }

        return uri;
    }

    private static boolean isForbiddenAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    /**
     * Sends an HTTP GET or POST JSON request and parses the JSON object response.
     */
    protected static JsonObject sendJsonRequest(String url, String apiKey, String jsonBody, int timeoutSeconds)
            throws IOException, InterruptedException {
        int effectiveTimeout = Math.max(10, Math.min(600, timeoutSeconds));
        URI validatedUri = validateOutboundUriForOcr(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(validatedUri)
                .timeout(Duration.ofSeconds(effectiveTimeout))
                .header("Accept", "application/json");
        if (!Strings.isNullOrEmpty(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.GET();
        }

        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = response.body() != null
                    ? response.body().substring(0, Math.min(300, response.body().length()))
                    : "";
            throw new IOException("HTTP " + response.statusCode() + " from " + url + ": " + snippet);
        }
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            return reader.readObject();
        }
    }
}
