package com.sismics.docs.core.util.plugin;

import com.google.common.base.Strings;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parsed plugin.json manifest.
 */
public class PluginManifest {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");

    public static class ProtectedTagSpec {
        private final String name;
        private final String color;
        private final String role;
        private final boolean hidden;
        private final String description;

        public ProtectedTagSpec(String name, String color, String role, boolean hidden) {
            this(name, color, role, hidden, null);
        }

        public ProtectedTagSpec(String name, String color, String role, boolean hidden, String description) {
            this.name = name;
            this.color = color;
            this.role = role;
            this.hidden = hidden;
            this.description = description;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
        public String getRole() { return role; }
        public boolean isHidden() { return hidden; }
        public String getDescription() { return description; }
    }

    private String id;
    private String name;
    private String version;
    private String description;
    private String entry;
    private String navLabel;
    private String navIcon;
    private String handler;
    private String rawJson;
    private final List<ProtectedTagSpec> protectedTags = new ArrayList<>();

    public static PluginManifest parse(String jsonText) {
        if (Strings.isNullOrEmpty(jsonText)) {
            throw new IllegalArgumentException("plugin.json must not be empty");
        }
        JsonObject obj;
        try (JsonReader reader = Json.createReader(new StringReader(jsonText))) {
            obj = reader.readObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid plugin.json format: " + e.getMessage(), e);
        }

        PluginManifest m = new PluginManifest();
        m.rawJson = jsonText;
        m.id = trimToNull(obj.getString("id", null));
        m.name = trimToNull(obj.getString("name", null));
        m.version = trimToNull(obj.getString("version", null));
        m.description = trimToNull(obj.getString("description", null));
        m.entry = trimToNull(obj.getString("entry", "index.html"));
        m.navLabel = trimToNull(obj.getString("nav_label", m.name));
        m.navIcon = trimToNull(obj.getString("nav_icon", "fas fa-puzzle-piece"));
        m.handler = trimToNull(obj.getString("handler", null));

        if (m.id == null || !ID_PATTERN.matcher(m.id).matches()) {
            throw new IllegalArgumentException("Invalid plugin id (must match ^[a-z0-9][a-z0-9-]{0,62}$)");
        }
        if (m.name == null) {
            throw new IllegalArgumentException("plugin.json missing required 'name'");
        }
        if (m.version == null) {
            throw new IllegalArgumentException("plugin.json missing required 'version'");
        }
        if (m.entry == null || m.entry.contains("..")) {
            throw new IllegalArgumentException("Invalid plugin 'entry' path");
        }
        if (m.entry.startsWith("/")) {
            m.entry = m.entry.substring(1);
        }

        if (obj.containsKey("protected_tags") && obj.get("protected_tags") instanceof JsonArray) {
            JsonArray arr = obj.getJsonArray("protected_tags");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject t = arr.getJsonObject(i);
                String tName = trimToNull(t.getString("name", null));
                String tColor = trimToNull(t.getString("color", "#3a87ad"));
                String tRole = trimToNull(t.getString("role", "PLUGIN_ROOT"));
                boolean tHidden = t.getBoolean("hidden", false);
                String tDescription = trimToNull(t.getString("description", null));
                if (tDescription != null) {
                    tDescription = tDescription.replaceAll("[\\r\\n]+", " ").trim();
                    if (tDescription.isEmpty()) {
                        tDescription = null;
                    } else if (tDescription.length() > 200) {
                        tDescription = tDescription.substring(0, 200);
                    }
                }
                if (tName != null) {
                    if (tName.length() > 36) {
                        tName = tName.substring(0, 36);
                    }
                    m.protectedTags.add(new ProtectedTagSpec(tName, tColor, tRole, tHidden, tDescription));
                }
            }
        }
        return m;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getEntry() { return entry; }
    public String getNavLabel() { return navLabel; }
    public String getNavIcon() { return navIcon; }
    public String getHandler() { return handler; }
    public String getRawJson() { return rawJson; }
    public List<ProtectedTagSpec> getProtectedTags() { return protectedTags; }
}
