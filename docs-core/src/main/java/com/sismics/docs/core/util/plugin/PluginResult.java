package com.sismics.docs.core.util.plugin;

import jakarta.json.JsonObject;

/**
 * Result returned by a {@link PluginHandler} action (either JSON or binary download).
 */
public class PluginResult {
    private final int statusCode;
    private final JsonObject json;
    private final byte[] binaryData;
    private final String contentType;
    private final String fileName;

    private PluginResult(int statusCode, JsonObject json, byte[] binaryData, String contentType, String fileName) {
        this.statusCode = statusCode;
        this.json = json;
        this.binaryData = binaryData;
        this.contentType = contentType;
        this.fileName = fileName;
    }

    public static PluginResult ok(JsonObject json) {
        return new PluginResult(200, json, null, "application/json", null);
    }

    public static PluginResult binary(byte[] bytes, String contentType, String fileName) {
        return new PluginResult(200, null, bytes, contentType, fileName);
    }

    public static PluginResult error(int statusCode, JsonObject json) {
        return new PluginResult(statusCode, json, null, "application/json", null);
    }

    public int getStatusCode() { return statusCode; }
    public JsonObject getJson() { return json; }
    public byte[] getBinaryData() { return binaryData; }
    public String getContentType() { return contentType; }
    public String getFileName() { return fileName; }
    public boolean isBinary() { return binaryData != null; }
}
