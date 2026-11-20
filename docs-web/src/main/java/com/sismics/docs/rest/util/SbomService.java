package com.sismics.docs.rest.util;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.PluginDao;
import com.sismics.docs.core.model.jpa.Plugin;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletContext;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * Runtime-aware Software Bill of Materials (SBOM) collector and exporter (JSON, CycloneDX 1.5, CSV).
 * Operates purely in-process without spawning external CLI subprocesses.
 */
public final class SbomService {
    private static final Logger log = LoggerFactory.getLogger(SbomService.class);

    private SbomService() {
    }

    public static final class ComponentItem {
        public final String category;
        public final String group;
        public final String name;
        public final String version;
        public final String status;
        public final String purl;

        public ComponentItem(String category, String group, String name, String version, String status, String purl) {
            this.category = Strings.nullToEmpty(category);
            this.group = Strings.nullToEmpty(group);
            this.name = Strings.nullToEmpty(name);
            this.version = Strings.isNullOrEmpty(version) ? "unknown" : version;
            this.status = Strings.nullToEmpty(status);
            this.purl = Strings.nullToEmpty(purl);
        }
    }

    public static JsonObject buildSbomJson(ServletContext servletContext) {
        String generatedAt = Instant.now().toString();
        JsonObject env = collectRuntimeEnvironment(servletContext);
        List<ComponentItem> plugins = collectPlugins();
        List<ComponentItem> backendLibs = collectBackendLibraries(servletContext);
        List<ComponentItem> frontendLibs = collectFrontendLibraries(servletContext);

        return Json.createObjectBuilder()
                .add("generated_at", generatedAt)
                .add("environment", env)
                .add("plugins", toJsonArray(plugins))
                .add("backend_libraries", toJsonArray(backendLibs))
                .add("frontend_libraries", toJsonArray(frontendLibs))
                .build();
    }

    public static String exportCycloneDxJson(ServletContext servletContext) {
        String generatedAt = Instant.now().toString();
        JsonObject env = collectRuntimeEnvironment(servletContext);
        List<ComponentItem> plugins = collectPlugins();
        List<ComponentItem> backendLibs = collectBackendLibraries(servletContext);
        List<ComponentItem> frontendLibs = collectFrontendLibraries(servletContext);

        JsonArrayBuilder components = Json.createArrayBuilder();

        // Runtime platform components (JVM, Server, Database)
        addCdxComponent(components, "platform", "java", env.getString("java_vendor", "Java") + " / JDK", env.getString("java_version", "unknown"), "");
        if (!env.getString("server_info", "").isEmpty()) {
            addCdxComponent(components, "application", "server", env.getString("server_info"), env.getString("server_version", "unknown"), "");
        }
        if (!env.getString("db_product", "").isEmpty()) {
            addCdxComponent(components, "application", "database", env.getString("db_product"), env.getString("db_version", "unknown"), "");
        }
        for (ComponentItem item : plugins) {
            addCdxComponent(components, "application", item.group, item.name, item.version, item.purl);
        }
        for (ComponentItem item : backendLibs) {
            addCdxComponent(components, "library", item.group, item.name, item.version, item.purl);
        }
        for (ComponentItem item : frontendLibs) {
            addCdxComponent(components, "library", item.group, item.name, item.version, item.purl);
        }

        JsonObject cdx = Json.createObjectBuilder()
                .add("bomFormat", "CycloneDX")
                .add("specVersion", "1.5")
                .add("serialNumber", "urn:uuid:" + UUID.randomUUID())
                .add("version", 1)
                .add("metadata", Json.createObjectBuilder()
                        .add("timestamp", generatedAt)
                        .add("component", Json.createObjectBuilder()
                                .add("type", "application")
                                .add("name", "FormosaBearV-Docs")
                                .add("version", env.getString("app_version", "unknown"))))
                .add("components", components)
                .build();

        return cdx.toString();
    }

    public static String exportCsv(ServletContext servletContext) {
        JsonObject env = collectRuntimeEnvironment(servletContext);
        List<ComponentItem> plugins = collectPlugins();
        List<ComponentItem> backendLibs = collectBackendLibraries(servletContext);
        List<ComponentItem> frontendLibs = collectFrontendLibraries(servletContext);

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Category,Group,Name,Version,Status\n");

        appendCsvRow(sb, "Environment", "application", "FormosaBearV-Docs", env.getString("app_version", ""), "ACTIVE");
        appendCsvRow(sb, "Environment", "os", env.getString("os_name", "") + " (" + env.getString("os_arch", "") + ")", env.getString("os_version", ""), "ACTIVE");
        appendCsvRow(sb, "Environment", "java", env.getString("jvm_name", "JVM"), env.getString("java_version", ""), env.getString("java_vendor", ""));
        appendCsvRow(sb, "Environment", "server", env.getString("server_info", ""), env.getString("server_version", ""), "ACTIVE");
        appendCsvRow(sb, "Environment", "database", env.getString("db_product", ""), env.getString("db_version", ""), env.getString("jdbc_driver", "") + " " + env.getString("jdbc_driver_version", ""));
        appendCsvRow(sb, "Environment", "ocr", env.getString("ocr_engine", ""), "", env.getBoolean("ocr_enabled", false) ? "ENABLED" : "DISABLED");

        for (ComponentItem item : plugins) {
            appendCsvRow(sb, "Plugin", item.group, item.name, item.version, item.status);
        }
        for (ComponentItem item : frontendLibs) {
            appendCsvRow(sb, "Frontend", item.group, item.name, item.version, item.status);
        }
        for (ComponentItem item : backendLibs) {
            appendCsvRow(sb, "Backend", item.group, item.name, item.version, item.status);
        }
        return sb.toString();
    }

    private static void addCdxComponent(JsonArrayBuilder arr, String type, String group, String name, String version, String purl) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("type", type)
                .add("name", Strings.nullToEmpty(name))
                .add("version", Strings.isNullOrEmpty(version) ? "unknown" : version);
        if (!Strings.isNullOrEmpty(group)) {
            b.add("group", group);
        }
        if (!Strings.isNullOrEmpty(purl)) {
            b.add("purl", purl);
        }
        arr.add(b);
    }

    private static void appendCsvRow(StringBuilder sb, String... cols) {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = cols[i] == null ? "" : cols[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append('\n');
    }

    private static JsonArray toJsonArray(List<ComponentItem> items) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (ComponentItem item : items) {
            arr.add(Json.createObjectBuilder()
                    .add("group", item.group)
                    .add("name", item.name)
                    .add("version", item.version)
                    .add("status", item.status));
        }
        return arr.build();
    }

    private static JsonObject collectRuntimeEnvironment(ServletContext servletContext) {
        String appVersion = "unknown";
        try {
            ResourceBundle configBundle = ConfigUtil.getConfigBundle();
            appVersion = configBundle.getString("api.current_version");
        } catch (Exception ignored) {
        }

        String serverInfo = "";
        String serverVersion = "";
        if (servletContext != null && !Strings.isNullOrEmpty(servletContext.getServerInfo())) {
            serverInfo = servletContext.getServerInfo().trim();
            int slashIdx = serverInfo.indexOf('/');
            serverVersion = slashIdx >= 0 ? serverInfo.substring(slashIdx + 1).trim() : serverInfo;
        }
        if (serverVersion.isEmpty()) {
            try {
                Class<?> jettyClass = Class.forName("org.eclipse.jetty.util.Jetty");
                Object ver = jettyClass.getField("VERSION").get(null);
                if (ver != null) {
                    serverInfo = "Eclipse Jetty";
                    serverVersion = ver.toString();
                }
            } catch (Throwable ignored) {
            }
        }

        final String[] dbMeta = new String[]{"unknown", "unknown", "unknown", "unknown"};
        try {
            EntityManager em = ThreadLocalContext.get().getEntityManager();
            if (em != null) {
                Session session = em.unwrap(Session.class);
                session.doWork(connection -> {
                    DatabaseMetaData md = connection.getMetaData();
                    dbMeta[0] = Strings.nullToEmpty(md.getDatabaseProductName());
                    dbMeta[1] = Strings.nullToEmpty(md.getDatabaseProductVersion());
                    dbMeta[2] = Strings.nullToEmpty(md.getDriverName());
                    dbMeta[3] = Strings.nullToEmpty(md.getDriverVersion());
                });
            }
        } catch (Exception e) {
            log.debug("Unable to read JDBC DatabaseMetaData for SBOM", e);
        }

        boolean ocrEnabled = Boolean.TRUE.equals(ConfigUtil.getConfigBooleanValue(ConfigType.OCR_ENABLED, false));
        String ocrEngine = ConfigUtil.getOcrConfig().getEngine();
        String defaultLanguage = Strings.nullToEmpty(ConfigUtil.getConfigStringValue(ConfigType.DEFAULT_LANGUAGE));

        return Json.createObjectBuilder()
                .add("app_version", Strings.nullToEmpty(appVersion))
                .add("os_name", System.getProperty("os.name", "unknown"))
                .add("os_version", System.getProperty("os.version", "unknown"))
                .add("os_arch", System.getProperty("os.arch", "unknown"))
                .add("java_version", System.getProperty("java.version", "unknown"))
                .add("java_vendor", System.getProperty("java.vendor", "unknown"))
                .add("jvm_name", System.getProperty("java.vm.name", "unknown"))
                .add("server_info", serverInfo.isEmpty() ? "unknown" : serverInfo)
                .add("server_version", serverVersion.isEmpty() ? "unknown" : serverVersion)
                .add("db_product", dbMeta[0])
                .add("db_version", dbMeta[1])
                .add("jdbc_driver", dbMeta[2])
                .add("jdbc_driver_version", dbMeta[3])
                .add("ocr_enabled", ocrEnabled)
                .add("ocr_engine", Strings.nullToEmpty(ocrEngine))
                .add("default_language", defaultLanguage)
                .build();
    }

    private static List<ComponentItem> collectPlugins() {
        List<ComponentItem> result = new ArrayList<>();
        try {
            List<Plugin> plugins = new PluginDao().findAll();
            for (Plugin p : plugins) {
                String status = p.isEnabled() ? "ENABLED" : "DISABLED";
                String group = Strings.isNullOrEmpty(p.getHandler()) ? "plugin" : p.getHandler();
                result.add(new ComponentItem("plugin", group, p.getName() + " (" + p.getId() + ")", p.getVersion(), status, ""));
            }
            result.sort(Comparator.comparing(c -> c.name.toLowerCase()));
        } catch (Exception e) {
            log.debug("Unable to read plugins for SBOM", e);
        }
        return result;
    }

    private static List<ComponentItem> collectBackendLibraries(ServletContext servletContext) {
        Map<String, ComponentItem> dedup = new LinkedHashMap<>();

        // 1. Prefer deployed WEB-INF/lib/*.jar inside WAR
        if (servletContext != null) {
            Set<String> paths = servletContext.getResourcePaths("/WEB-INF/lib/");
            if (paths != null && !paths.isEmpty()) {
                for (String path : paths) {
                    if (!path.endsWith(".jar")) {
                        continue;
                    }
                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                    try (InputStream is = servletContext.getResourceAsStream(path)) {
                        if (is != null) {
                            ComponentItem item = inspectJarStream(fileName, is);
                            dedup.putIfAbsent(item.group + ":" + item.name, item);
                        }
                    } catch (Exception e) {
                        log.debug("Unable to inspect JAR stream {}", fileName, e);
                    }
                }
            }
        }

        // 2. Fallback to java.class.path JARs (useful in embedded/test containers)
        if (dedup.isEmpty()) {
            String cp = System.getProperty("java.class.path", "");
            for (String entry : cp.split(Pattern.quote(File.pathSeparator))) {
                if (!entry.endsWith(".jar")) {
                    continue;
                }
                File f = new File(entry);
                if (!f.isFile()) {
                    continue;
                }
                try (JarFile jf = new JarFile(f)) {
                    ComponentItem item = inspectJarFile(f.getName(), jf);
                    dedup.putIfAbsent(item.group + ":" + item.name, item);
                } catch (Exception e) {
                    log.debug("Unable to inspect classpath JAR {}", f.getName(), e);
                }
            }
        }

        List<ComponentItem> list = new ArrayList<>(dedup.values());
        list.sort(Comparator.comparing((ComponentItem c) -> c.group.toLowerCase())
                .thenComparing(c -> c.name.toLowerCase()));
        return list;
    }

    private static ComponentItem inspectJarStream(String fileName, InputStream is) throws Exception {
        String group = "";
        String name = "";
        String version = "";
        Manifest manifest = null;

        try (JarInputStream jis = new JarInputStream(is)) {
            manifest = jis.getManifest();
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("META-INF/maven/") && entryName.endsWith("/pom.properties")) {
                    Properties props = new Properties();
                    props.load(jis);
                    group = Strings.nullToEmpty(props.getProperty("groupId")).trim();
                    name = Strings.nullToEmpty(props.getProperty("artifactId")).trim();
                    version = Strings.nullToEmpty(props.getProperty("version")).trim();
                    if (!name.isEmpty() && !version.isEmpty()) {
                        break;
                    }
                }
            }
        }

        return finalizeJarComponent(fileName, group, name, version, manifest);
    }

    private static ComponentItem inspectJarFile(String fileName, JarFile jf) throws Exception {
        String group = "";
        String name = "";
        String version = "";
        Manifest manifest = jf.getManifest();

        for (JarEntry entry : Collections.list(jf.entries())) {
            String entryName = entry.getName();
            if (entryName.startsWith("META-INF/maven/") && entryName.endsWith("/pom.properties")) {
                try (InputStream is = jf.getInputStream(entry)) {
                    Properties props = new Properties();
                    props.load(is);
                    group = Strings.nullToEmpty(props.getProperty("groupId")).trim();
                    name = Strings.nullToEmpty(props.getProperty("artifactId")).trim();
                    version = Strings.nullToEmpty(props.getProperty("version")).trim();
                    if (!name.isEmpty() && !version.isEmpty()) {
                        break;
                    }
                }
            }
        }

        return finalizeJarComponent(fileName, group, name, version, manifest);
    }

    private static ComponentItem finalizeJarComponent(String fileName, String group, String name, String version, Manifest manifest) {
        if ((name.isEmpty() || version.isEmpty()) && manifest != null) {
            Attributes attrs = manifest.getMainAttributes();
            if (group.isEmpty()) {
                group = firstNonEmpty(attrs.getValue("Implementation-Vendor-Id"), attrs.getValue("Bundle-Vendor"), "");
            }
            if (name.isEmpty()) {
                name = firstNonEmpty(attrs.getValue("Automatic-Module-Name"), attrs.getValue("Bundle-SymbolicName"), attrs.getValue("Implementation-Title"), "");
            }
            if (version.isEmpty()) {
                version = firstNonEmpty(attrs.getValue("Implementation-Version"), attrs.getValue("Bundle-Version"), attrs.getValue("Specification-Version"), "");
            }
        }

        if (fileName.endsWith(".jar")) {
            String base = fileName.substring(0, fileName.length() - 4);
            int dashVersionIdx = -1;
            for (int i = 1; i < base.length() - 1; i++) {
                if (base.charAt(i) == '-' && Character.isDigit(base.charAt(i + 1))) {
                    dashVersionIdx = i;
                    break;
                }
            }
            if (dashVersionIdx > 0) {
                if (name.isEmpty()) {
                    name = base.substring(0, dashVersionIdx);
                }
                if (version.isEmpty()) {
                    version = base.substring(dashVersionIdx + 1);
                }
            } else if (name.isEmpty()) {
                name = base;
            }
        } else if (name.isEmpty()) {
            name = fileName;
        }

        // Clean OSGi Bundle-SymbolicName parameters like ";singleton:=true"
        int semi = name.indexOf(';');
        if (semi > 0) {
            name = name.substring(0, semi).trim();
        }
        String purl = (!group.isEmpty() && !name.isEmpty() && !version.isEmpty())
                ? "pkg:maven/" + group + "/" + name + "@" + version
                : "";
        return new ComponentItem("backend", group, name, version.isEmpty() ? "unknown" : version, "LOADED", purl);
    }

    private static List<ComponentItem> collectFrontendLibraries(ServletContext servletContext) {
        List<ComponentItem> result = new ArrayList<>();
        if (servletContext == null) {
            return result;
        }
        try (InputStream is = servletContext.getResourceAsStream("/sbom-frontend.json")) {
            if (is != null) {
                try (JsonReader reader = Json.createReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    JsonArray arr = reader.readArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject obj = arr.getJsonObject(i);
                        String name = obj.getString("name", "");
                        String version = obj.getString("version", "unknown");
                        String group = obj.getString("group", "frontend");
                        result.add(new ComponentItem("frontend", group, name, version, "BUNDLED", ""));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Unable to read /sbom-frontend.json", e);
        }
        result.sort(Comparator.comparing(c -> c.name.toLowerCase()));
        return result;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }
}
