package com.sismics.docs.core.util.plugin;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.ConfigDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.event.DocumentCreatedAsyncEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.Acl;
import com.sismics.docs.core.model.jpa.Config;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.DocumentTag;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.FileUtil;
import com.sismics.docs.core.util.TransactionUtil;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Seeds bundled {@code plugin-*.zip} archives from {@code classpath:plugins/} into an
 * admin-only {@code plugins} document tagged with {@code plugins} on application startup.
 */
public final class PluginSeedService {
    private static final Logger log = LoggerFactory.getLogger(PluginSeedService.class);

    private static final String PLUGINS_RESOURCE_DIR = "plugins";
    private static final String PLUGINS_TITLE = "plugins";
    private static final String PLUGINS_TAG_COLOR = "#3a87ad";
    private static final String PLUGIN_ZIP_PREFIX = "plugin-";
    private static final String PLUGIN_ZIP_SUFFIX = ".zip";

    private PluginSeedService() {
    }

    /**
     * Seeds any bundled {@code plugin-*.zip} archives that have not yet been recorded in
     * {@link ConfigType#PLUGINS_SEEDED}. Safe to call repeatedly across restarts.
     */
    public static void seedBundledPlugins() {
        Set<String> bundledFiles = discoverBundledPluginZips();
        if (bundledFiles.isEmpty()) {
            return;
        }

        TransactionUtil.handle(() -> {
            if (!ConfigUtil.isFileEncryptLocked()) {
                log.debug("Skipping bundled plugin seed until file encryption mode is locked");
                return;
            }

            User admin = resolveAdminUser();
            if (admin == null) {
                log.debug("Skipping bundled plugin seed because admin user is not initialized");
                return;
            }

            ConfigDao configDao = new ConfigDao();
            Config seededConfig = configDao.getById(ConfigType.PLUGINS_SEEDED);
            Set<String> seeded = parseSeededSet(seededConfig == null ? null : seededConfig.getValue());

            List<String> unseeded = new ArrayList<>();
            for (String fileName : bundledFiles) {
                if (!seeded.contains(fileName)) {
                    unseeded.add(fileName);
                }
            }
            EntityManager em = ThreadLocalContext.get().getEntityManager();
            String description = buildPluginsDescription(bundledFiles);
            if (unseeded.isEmpty()) {
                List<Document> existingDocs = em.createQuery(
                        "select d from Document d where d.title = :title and d.userId = :userId and d.deleteDate is null",
                        Document.class)
                        .setParameter("title", PLUGINS_TITLE)
                        .setParameter("userId", admin.getId())
                        .setMaxResults(1)
                        .getResultList();
                if (!existingDocs.isEmpty()) {
                    Document existingDoc = existingDocs.get(0);
                    if (!Strings.isNullOrEmpty(description) && !description.equals(existingDoc.getDescription())) {
                        existingDoc.setDescription(description);
                        new DocumentDao().update(existingDoc, admin.getId());
                    }
                }
                return;
            }

            String tagId = findOrCreatePluginsTag(em, admin.getId());
            Document document = findOrCreatePluginsDocument(em, admin.getId(), tagId, description);

            boolean updated = false;
            for (String fileName : unseeded) {
                if (seedSinglePluginZip(fileName, document, admin.getId())) {
                    seeded.add(fileName);
                    updated = true;
                }
            }

            if (updated) {
                configDao.update(ConfigType.PLUGINS_SEEDED, String.join(",", seeded));
            }
        });
    }

    private static User resolveAdminUser() {
        UserDao userDao = new UserDao();
        User admin = userDao.getById("admin");
        if (admin != null && admin.getDeleteDate() == null) {
            return admin;
        }
        return userDao.getActiveByUsername("admin");
    }

    private static Set<String> parseSeededSet(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (Strings.isNullOrEmpty(raw)) {
            return result;
        }
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String findOrCreatePluginsTag(EntityManager em, String adminId) {
        List<Tag> existing = em.createQuery(
                "select t from Tag t where t.name = :name and t.userId = :userId and t.deleteDate is null",
                Tag.class)
                .setParameter("name", PLUGINS_TITLE)
                .setParameter("userId", adminId)
                .setMaxResults(1)
                .getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }

        Tag tag = new Tag();
        tag.setName(PLUGINS_TITLE);
        tag.setColor(PLUGINS_TAG_COLOR);
        tag.setUserId(adminId);
        tag.setCreateDate(new Date());
        tag.setHidden(true);
        String tagId = new TagDao().create(tag, adminId);
        grantAdminReadWriteAcl(tagId, adminId);
        return tagId;
    }

    private static Document findOrCreatePluginsDocument(EntityManager em, String adminId, String tagId, String description) {
        List<Document> existing = em.createQuery(
                "select d from Document d where d.title = :title and d.userId = :userId and d.deleteDate is null",
                Document.class)
                .setParameter("title", PLUGINS_TITLE)
                .setParameter("userId", adminId)
                .setMaxResults(1)
                .getResultList();

        TagDao tagDao = new TagDao();
        DocumentDao documentDao = new DocumentDao();
        if (!existing.isEmpty()) {
            Document doc = existing.get(0);
            ensureDocumentHasTag(em, tagDao, doc.getId(), tagId);
            if (!Strings.isNullOrEmpty(description) && !description.equals(doc.getDescription())) {
                doc.setDescription(description);
                documentDao.update(doc, adminId);
            }
            return doc;
        }

        Document doc = new Document();
        doc.setUserId(adminId);
        doc.setTitle(PLUGINS_TITLE);
        doc.setDescription(description);
        doc.setLanguage("eng");
        doc.setCreateDate(new Date());
        String docId = documentDao.create(doc, adminId);

        grantAdminReadWriteAcl(docId, adminId);
        tagDao.updateTagList(docId, Set.of(tagId));

        DocumentCreatedAsyncEvent event = new DocumentCreatedAsyncEvent();
        event.setUserId(adminId);
        event.setDocumentId(docId);
        ThreadLocalContext.get().addAsyncEvent(event);

        return doc;
    }

    private static String buildPluginsDescription(Set<String> bundledFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ul>")
          .append("<li><strong>[EN]</strong> Bundled plugin packages. Download the <code>.zip</code> file below, then upload and enable it in <strong>Settings &rarr; Plugins</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[zh_TW]</strong> 內建外掛安裝包：請下載下方 <code>.zip</code> 檔案，至「<strong>設定 &rarr; 外掛程式</strong>（<code>#/settings/plugins</code>）」上傳並啟用。</li>")
          .append("<li><strong>[zh_CN]</strong> 内置插件安装包：请下载下方 <code>.zip</code> 文件，至“<strong>设置 &rarr; 插件</strong>（<code>#/settings/plugins</code>）”上传并启用。</li>")
          .append("<li><strong>[ja]</strong> 同梱プラグインパッケージ：下の <code>.zip</code> をダウンロードし、「<strong>設定 &rarr; プラグイン</strong>（<code>#/settings/plugins</code>）」でアップロードして有効化してください。</li>")
          .append("<li><strong>[vi]</strong> Gói plugin đi kèm: Tải tệp <code>.zip</code> bên dưới và tải lên/bật tại <strong>Cài đặt &rarr; Plugin</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[de]</strong> Mitgelieferte Plugin-Pakete: Laden Sie die <code>.zip</code>-Datei herunter und aktivieren Sie sie unter <strong>Einstellungen &rarr; Plugins</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[fr]</strong> Extensions intégrées : Téléchargez le fichier <code>.zip</code> et activez-le dans <strong>Paramètres &rarr; Extensions</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[es]</strong> Complementos incluidos: Descargue el archivo <code>.zip</code> y actívelo en <strong>Configuración &rarr; Complementos</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[pt]</strong> Plugins incluídos: Transfira o ficheiro <code>.zip</code> e ative-o em <strong>Definições &rarr; Plugins</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[pt_BR]</strong> Plugins incluídos: Baixe o arquivo <code>.zip</code> e ative-o em <strong>Configurações &rarr; Plugins</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[it]</strong> Plugin inclusi: Scarica il file <code>.zip</code> e attivalo in <strong>Impostazioni &rarr; Plugin</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[pl]</strong> Wbudowane wtyczki: Pobierz plik <code>.zip</code> i włącz go w <strong>Ustawienia &rarr; Wtyczki</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[ru]</strong> Встроенные плагины: скачайте <code>.zip</code>-файл и включите его в разделе «<strong>Настройки &rarr; Плагины</strong>» (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[el]</strong> Ενσωματωμένα πρόσθετα: Κατεβάστε το αρχείο <code>.zip</code> και ενεργοποιήστε το στις <strong>Ρυθμίσεις &rarr; Πρόσθετα</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[sq_AL]</strong> Shtojcat e përfshira: Shkarkoni skedarin <code>.zip</code> dhe aktivizojeni te <strong>Cilësimet &rarr; Shtojcat</strong> (<code>#/settings/plugins</code>).</li>")
          .append("<li><strong>[sr]</strong> Уграђени додаци: преузмите <code>.zip</code> датотеку и омогућите је у <strong>Подешавања &rarr; Додаци</strong> (<code>#/settings/plugins</code>).</li>")
          .append("</ul>")
          .append("<p><strong>Included files:</strong></p><ul>");
        for (String fileName : bundledFiles) {
            sb.append("<li><strong>").append(fileName).append("</strong>");
            String summary = readPluginSummaryFromZip(fileName);
            if (!Strings.isNullOrEmpty(summary)) {
                sb.append(": ").append(summary);
            }
            sb.append("</li>");
        }
        sb.append("</ul>");
        return StringUtils.abbreviate(sb.toString(), 4000);
    }

    private static String readPluginSummaryFromZip(String fileName) {
        String resourcePath = PLUGINS_RESOURCE_DIR + "/" + fileName;
        ClassLoader cl = PluginSeedService.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            try (ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && "plugin.json".equals(entry.getName())) {
                        try (JsonReader reader = Json.createReader(new InputStreamReader(zis, StandardCharsets.UTF_8))) {
                            JsonObject obj = reader.readObject();
                            String name = obj.getString("name", "");
                            String version = obj.getString("version", "");
                            String desc = obj.getString("description", "");
                            StringBuilder info = new StringBuilder();
                            if (!name.isEmpty()) {
                                info.append(name);
                            }
                            if (!version.isEmpty()) {
                                if (info.length() > 0) {
                                    info.append(' ');
                                }
                                info.append("(v").append(version).append(')');
                            }
                            if (!desc.isEmpty()) {
                                if (info.length() > 0) {
                                    info.append(" - ");
                                }
                                info.append(desc);
                            }
                            return info.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not read plugin.json summary from {}", fileName, e);
        }
        return null;
    }

    private static void ensureDocumentHasTag(EntityManager em, TagDao tagDao, String documentId, String tagId) {
        List<DocumentTag> currentTags = em.createQuery(
                "select dt from DocumentTag dt where dt.documentId = :documentId and dt.deleteDate is null",
                DocumentTag.class)
                .setParameter("documentId", documentId)
                .getResultList();
        Set<String> tagIds = new HashSet<>();
        boolean alreadyPresent = false;
        for (DocumentTag dt : currentTags) {
            tagIds.add(dt.getTagId());
            if (tagId.equals(dt.getTagId())) {
                alreadyPresent = true;
            }
        }
        if (!alreadyPresent) {
            tagIds.add(tagId);
            tagDao.updateTagList(documentId, tagIds);
        }
    }

    private static void grantAdminReadWriteAcl(String sourceId, String adminId) {
        AclDao aclDao = new AclDao();
        for (PermType perm : new PermType[]{PermType.READ, PermType.WRITE}) {
            Acl acl = new Acl();
            acl.setPerm(perm);
            acl.setType(AclType.USER);
            acl.setSourceId(sourceId);
            acl.setTargetId(adminId);
            aclDao.create(acl, adminId);
        }
    }

    private static boolean seedSinglePluginZip(String fileName, Document document, String adminId) {
        for (File existingFile : new FileDao().getByDocumentId(adminId, document.getId())) {
            if (fileName.equals(existingFile.getName())) {
                return true;
            }
        }
        String resourcePath = PLUGINS_RESOURCE_DIR + "/" + fileName;
        ClassLoader cl = PluginSeedService.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Bundled plugin resource not found on classpath: {}", resourcePath);
                return false;
            }
            Path tempFile = AppContext.getInstance().getFileService().createTemporaryFile(fileName);
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            FileUtil.createFile(fileName, null, tempFile, Files.size(tempFile),
                    document.getLanguage(), adminId, document.getId());
            log.info("Seeded bundled plugin '{}' into document '{}'", fileName, document.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to seed bundled plugin '{}'", fileName, e);
            return false;
        }
    }

    /**
     * Discovers all {@code plugin-*.zip} files located directly under {@code classpath:plugins/}.
     */
    static Set<String> discoverBundledPluginZips() {
        Set<String> result = new TreeSet<>();
        ClassLoader cl = PluginSeedService.class.getClassLoader();
        try {
            Enumeration<URL> urls = cl.getResources(PLUGINS_RESOURCE_DIR);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    collectFromDirectory(Paths.get(url.toURI()), result);
                } else if ("jar".equals(protocol)) {
                    collectFromJar(url, result);
                }
            }
        } catch (IOException | URISyntaxException e) {
            log.warn("Unable to scan bundled plugins directory on classpath", e);
        }
        return result;
    }

    private static void collectFromDirectory(Path dir, Set<String> result) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(PluginSeedService::isPluginZipName)
                    .forEach(result::add);
        }
    }

    private static void collectFromJar(URL url, Set<String> result) throws IOException {
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        conn.setUseCaches(false);
        String prefix = PLUGINS_RESOURCE_DIR + "/";
        try (JarFile jarFile = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith(prefix)) {
                    String fileName = name.substring(prefix.length());
                    if (!fileName.contains("/") && isPluginZipName(fileName)) {
                        result.add(fileName);
                    }
                }
            }
        }
    }

    private static boolean isPluginZipName(String fileName) {
        return fileName.startsWith(PLUGIN_ZIP_PREFIX) && fileName.endsWith(PLUGIN_ZIP_SUFFIX);
    }
}
