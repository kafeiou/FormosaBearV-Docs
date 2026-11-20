package com.sismics.docs.core.util.plugin;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.PluginDao;
import com.sismics.docs.core.dao.PluginDataDao;
import com.sismics.docs.core.dao.PluginTagDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.criteria.TagCriteria;
import com.sismics.docs.core.dao.dto.TagDto;
import com.sismics.docs.core.model.jpa.Acl;
import com.sismics.docs.core.model.jpa.Plugin;
import com.sismics.docs.core.model.jpa.PluginTag;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.util.plugin.exam.ExamPluginHandler;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central plugin lifecycle, protected-tag binding, handler registry, and secret-field masking service.
 */
public class PluginService {
    private static final Map<String, PluginHandler> HANDLERS = new LinkedHashMap<>();

    static {
        registerHandler(new ExamPluginHandler());
    }

    public static void registerHandler(PluginHandler handler) {
        HANDLERS.put(handler.getId(), handler);
    }

    public static PluginHandler getHandler(String handlerId) {
        if (Strings.isNullOrEmpty(handlerId)) {
            return null;
        }
        return HANDLERS.get(handlerId.trim());
    }

    /**
     * Installs or upgrades a plugin from its parsed manifest and binds declared protected tags.
     */
    public static Plugin registerOrUpgrade(PluginManifest manifest, String installerUserId) {
        PluginDao pluginDao = new PluginDao();
        Plugin existing = pluginDao.getById(manifest.getId());

        Plugin plugin = new Plugin();
        plugin.setId(manifest.getId());
        plugin.setName(manifest.getName());
        plugin.setVersion(manifest.getVersion());
        plugin.setDescription(manifest.getDescription());
        plugin.setEntry(manifest.getEntry());
        plugin.setNavLabel(manifest.getNavLabel());
        plugin.setNavIcon(manifest.getNavIcon());
        plugin.setHandler(manifest.getHandler());
        plugin.setManifest(manifest.getRawJson());
        // Preserve enabled state on upgrade; new installs default to disabled until admin enables
        plugin.setEnabled(existing != null && existing.isEnabled());
        Plugin saved = pluginDao.createOrUpdate(plugin);

        // Reconcile fixed protected tags declared by plugin.json
        PluginTagDao pluginTagDao = new PluginTagDao();
        pluginTagDao.deleteByPluginId(saved.getId());
        TagDao tagDao = new TagDao();
        AclDao aclDao = new AclDao();
        for (PluginManifest.ProtectedTagSpec spec : manifest.getProtectedTags()) {
            TagDto existingTag = tagDao.findByCriteria(new TagCriteria(), null).stream()
                    .filter(t -> t.getParentId() == null && spec.getName().equals(t.getName()))
                    .findFirst()
                    .orElse(null);
            String tagId;
            if (existingTag == null) {
                Tag newTag = new Tag();
                newTag.setName(spec.getName());
                newTag.setColor(spec.getColor());
                newTag.setDescription(spec.getDescription());
                newTag.setUserId(installerUserId);
                newTag.setInheritAcl(true);
                newTag.setHidden(spec.isHidden());
                tagId = tagDao.create(newTag, installerUserId);

                Acl readAcl = new Acl();
                readAcl.setType(AclType.USER);
                readAcl.setPerm(PermType.READ);
                readAcl.setSourceId(tagId);
                readAcl.setTargetId(installerUserId);
                aclDao.create(readAcl, installerUserId);

                Acl writeAcl = new Acl();
                writeAcl.setType(AclType.USER);
                writeAcl.setPerm(PermType.WRITE);
                writeAcl.setSourceId(tagId);
                writeAcl.setTargetId(installerUserId);
                aclDao.create(writeAcl, installerUserId);
            } else {
                tagId = existingTag.getId();
                Tag tagEntity = tagDao.getById(tagId);
                if (tagEntity != null) {
                    boolean changed = false;
                    if (tagEntity.isHidden() != spec.isHidden()) {
                        tagEntity.setHidden(spec.isHidden());
                        changed = true;
                    }
                    if (spec.getDescription() != null
                            && (tagEntity.getDescription() == null || tagEntity.getDescription().trim().isEmpty())) {
                        tagEntity.setDescription(spec.getDescription());
                        changed = true;
                    }
                    if (changed) {
                        tagDao.update(tagEntity, installerUserId);
                    }
                }
            }
            PluginTag pt = new PluginTag();
            pt.setPluginId(saved.getId());
            pt.setTagId(tagId);
            pt.setRole(spec.getRole());
            pluginTagDao.create(pt);
        }

        return saved;
    }

    /**
     * Uninstalls a plugin: removes plugin files, plugin data, and protected-tag locks (keeping T_TAG rows intact).
     */
    public static void uninstall(String pluginId) {
        new PluginTagDao().deleteByPluginId(pluginId);
        new PluginDataDao().deleteByPluginId(pluginId);
        new PluginDao().delete(pluginId);
        PluginPackageUtil.deletePluginFiles(pluginId);
    }

    /**
     * Triggers the handler's {@link PluginHandler#onEnabled(String)} lifecycle hook when a plugin is enabled.
     */
    public static void onEnabled(Plugin plugin, String installerUserId) {
        if (plugin == null) {
            return;
        }
        PluginHandler handler = getHandler(plugin.getHandler());
        if (handler != null) {
            try {
                handler.onEnabled(installerUserId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(PluginService.class)
                        .error("Error running onEnabled hook for plugin {}", plugin.getId(), e);
            }
        }
    }

    /**
     * Checks all installed plugins' handlers to see if any plugin data actively references the given tagId.
     */
    public static Optional<String> findTagReferenceReason(String tagId) {
        if (Strings.isNullOrEmpty(tagId)) {
            return Optional.empty();
        }
        List<Plugin> installed = new PluginDao().findAll();
        for (Plugin p : installed) {
            PluginHandler handler = getHandler(p.getHandler());
            if (handler != null) {
                Optional<String> reason = handler.findTagReferenceReason(tagId);
                if (reason.isPresent()) {
                    return reason;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Masks secret fields (keys starting with '_' or 'secret_' or 'correct_') for non-admin callers.
     */
    public static String maskSecretJsonIfNeeded(String rawJson, boolean isAdmin) {
        if (isAdmin || Strings.isNullOrEmpty(rawJson)) {
            return rawJson;
        }
        try (JsonReader reader = Json.createReader(new StringReader(rawJson))) {
            JsonValue val = reader.readValue();
            return maskValue(val).toString();
        } catch (Exception e) {
            return rawJson;
        }
    }

    private static JsonValue maskValue(JsonValue val) {
        if (val instanceof JsonObject) {
            JsonObject obj = (JsonObject) val;
            JsonObjectBuilder b = Json.createObjectBuilder();
            for (String k : obj.keySet()) {
                String lower = k.toLowerCase();
                if (k.startsWith("_") || lower.startsWith("secret_") || lower.startsWith("correct_")) {
                    continue;
                }
                b.add(k, maskValue(obj.get(k)));
            }
            return b.build();
        }
        if (val instanceof jakarta.json.JsonArray) {
            JsonArrayBuilder arr = Json.createArrayBuilder();
            for (JsonValue item : (jakarta.json.JsonArray) val) {
                arr.add(maskValue(item));
            }
            return arr.build();
        }
        return val;
    }

    public static List<PluginTag> getProtectedTags(String pluginId) {
        return new PluginTagDao().findByPluginId(pluginId);
    }
}
