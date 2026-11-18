package com.sismics.docs.rest.resource;

import com.google.common.base.Strings;
import com.sismics.docs.core.dao.PluginDao;
import com.sismics.docs.core.dao.PluginDataDao;
import com.sismics.docs.core.dao.PluginTagDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.model.jpa.Plugin;
import com.sismics.docs.core.model.jpa.PluginData;
import com.sismics.docs.core.model.jpa.PluginTag;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.plugin.PluginContext;
import com.sismics.docs.core.util.plugin.PluginHandler;
import com.sismics.docs.core.util.plugin.PluginManifest;
import com.sismics.docs.core.util.plugin.PluginPackageUtil;
import com.sismics.docs.core.util.plugin.PluginResult;
import com.sismics.docs.core.util.plugin.PluginService;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.util.JsonUtil;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plugin platform REST resource (/api/plugin).
 */
@Path("/plugin")
public class PluginResource extends BaseResource {

    private static JsonObjectBuilder toPluginJson(Plugin p, boolean includeTags) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("id", p.getId())
                .add("name", p.getName())
                .add("version", p.getVersion())
                .add("description", JsonUtil.nullable(p.getDescription()))
                .add("entry", p.getEntry())
                .add("nav_label", JsonUtil.nullable(p.getNavLabel()))
                .add("nav_icon", JsonUtil.nullable(p.getNavIcon()))
                .add("handler", JsonUtil.nullable(p.getHandler()))
                .add("enabled", p.isEnabled())
                .add("create_date", p.getCreateDate().getTime())
                .add("update_date", p.getUpdateDate().getTime());

        if (!Strings.isNullOrEmpty(p.getManifest())) {
            try (jakarta.json.JsonReader reader = Json.createReader(new java.io.StringReader(p.getManifest()))) {
                jakarta.json.JsonObject mf = reader.readObject();
                if (mf.containsKey("nav_label_i18n") && mf.get("nav_label_i18n") instanceof jakarta.json.JsonObject) {
                    b.add("nav_label_i18n", mf.getJsonObject("nav_label_i18n"));
                }
                if (mf.containsKey("default_locale")) {
                    b.add("default_locale", mf.getString("default_locale", "en"));
                }
            } catch (Exception ignored) {
            }
        }

        if (includeTags) {
            JsonArrayBuilder tagsArr = Json.createArrayBuilder();
            TagDao tagDao = new TagDao();
            for (PluginTag pt : new PluginTagDao().findByPluginId(p.getId())) {
                Tag tag = tagDao.getById(pt.getTagId());
                tagsArr.add(Json.createObjectBuilder()
                        .add("tag_id", pt.getTagId())
                        .add("role", pt.getRole())
                        .add("name", tag != null ? tag.getName() : "")
                        .add("color", tag != null ? tag.getColor() : "#3a87ad"));
            }
            b.add("protected_tags", tagsArr);
        }
        return b;
    }

    @GET
    @Path("list")
    public Response listAll() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (Plugin p : new PluginDao().findAll()) {
            arr.add(toPluginJson(p, true));
        }
        return Response.ok().entity(Json.createObjectBuilder().add("plugins", arr).build()).build();
    }

    @GET
    @Path("enabled")
    public Response listEnabled() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (Plugin p : new PluginDao().findEnabled()) {
            arr.add(toPluginJson(p, false));
        }
        return Response.ok().entity(Json.createObjectBuilder().add("plugins", arr).build()).build();
    }

    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@FormDataParam("file") FormDataBodyPart fileBodyPart) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);
        if (fileBodyPart == null) {
            throw new ClientException("ValidationError", "Missing plugin .zip file");
        }

        try (InputStream is = fileBodyPart.getValueAs(InputStream.class)) {
            PluginManifest manifest = PluginPackageUtil.installPackage(is);
            Plugin saved = PluginService.registerOrUpgrade(manifest, principal.getId());
            return Response.ok().entity(toPluginJson(saved, true).build()).build();
        } catch (IllegalArgumentException e) {
            throw new ClientException("InvalidPluginPackage", e.getMessage(), e);
        } catch (Exception e) {
            throw new ServerException("PluginInstallError", "Error installing plugin package", e);
        }
    }

    @POST
    @Path("{id: [a-z0-9\\-]+}/enable")
    public Response enable(@PathParam("id") String id) {
        return setPluginEnabled(id, true);
    }

    @POST
    @Path("{id: [a-z0-9\\-]+}/disable")
    public Response disable(@PathParam("id") String id) {
        return setPluginEnabled(id, false);
    }

    private Response setPluginEnabled(String id, boolean enabled) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        PluginDao pluginDao = new PluginDao();
        Plugin plugin = pluginDao.getById(id);
        if (plugin == null) {
            throw new NotFoundException();
        }
        pluginDao.setEnabled(id, enabled);
        return Response.ok().entity(Json.createObjectBuilder().add("status", "ok").add("enabled", enabled).build()).build();
    }

    @DELETE
    @Path("{id: [a-z0-9\\-]+}")
    public Response uninstall(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        Plugin plugin = new PluginDao().getById(id);
        if (plugin == null) {
            throw new NotFoundException();
        }
        PluginService.uninstall(id);
        return Response.ok().entity(Json.createObjectBuilder().add("status", "ok").build()).build();
    }

    @GET
    @Path("{id: [a-z0-9\\-]+}/assets/{path: .+}")
    public Response getAsset(@PathParam("id") String id, @PathParam("path") String assetPath) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        Plugin plugin = new PluginDao().getById(id);
        if (plugin == null || (!plugin.isEnabled() && !hasBaseFunction(BaseFunction.ADMIN))) {
            throw new ForbiddenClientException();
        }
        java.nio.file.Path file = PluginPackageUtil.resolvePluginAsset(id, assetPath);
        if (file == null) {
            throw new NotFoundException();
        }

        try {
            byte[] bytes = Files.readAllBytes(file);
            return Response.ok(bytes, detectContentType(file.getFileName().toString()))
                    .header("Cache-Control", "no-cache")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; script-src 'self'; object-src 'none'; frame-ancestors 'self'")
                    .build();
        } catch (Exception e) {
            throw new ServerException("PluginAssetError", "Unable to read plugin asset", e);
        }
    }

    @GET
    @Path("{id: [a-z0-9\\-]+}/data/{collection: [a-zA-Z0-9_\\-]+}")
    public Response listData(@PathParam("id") String id, @PathParam("collection") String collection) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        Plugin plugin = requireEnabledPlugin(id);
        boolean isAdmin = hasBaseFunction(BaseFunction.ADMIN);
        List<PluginData> items = new PluginDataDao().findByCollection(plugin.getId(), collection);
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (PluginData item : items) {
            arr.add(Json.createObjectBuilder()
                    .add("key", item.getKey())
                    .add("user_id", JsonUtil.nullable(item.getUserId()))
                    .add("value", PluginService.maskSecretJsonIfNeeded(item.getValue(), isAdmin))
                    .add("update_date", item.getUpdateDate().getTime()));
        }
        return Response.ok().entity(Json.createObjectBuilder().add("items", arr).build()).build();
    }

    @PUT
    @Path("{id: [a-z0-9\\-]+}/data/{collection: [a-zA-Z0-9_\\-]+}/{key: [a-zA-Z0-9_:\\-]+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putData(@PathParam("id") String id,
                            @PathParam("collection") String collection,
                            @PathParam("key") String key,
                            String body) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        Plugin plugin = requireEnabledPlugin(id);
        PluginDataDao dao = new PluginDataDao();
        PluginData existing = dao.getByKey(plugin.getId(), collection, key);
        if (existing != null && !hasBaseFunction(BaseFunction.ADMIN) && !principal.getId().equals(existing.getUserId())) {
            throw new ForbiddenClientException();
        }
        dao.upsert(plugin.getId(), collection, key, principal.getId(), Strings.nullToEmpty(body));
        return Response.ok().entity(Json.createObjectBuilder().add("status", "ok").build()).build();
    }

    @DELETE
    @Path("{id: [a-z0-9\\-]+}/data/{collection: [a-zA-Z0-9_\\-]+}/{key: [a-zA-Z0-9_:\\-]+}")
    public Response deleteData(@PathParam("id") String id,
                               @PathParam("collection") String collection,
                               @PathParam("key") String key) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        Plugin plugin = requireEnabledPlugin(id);
        PluginDataDao dao = new PluginDataDao();
        PluginData existing = dao.getByKey(plugin.getId(), collection, key);
        if (existing == null) {
            throw new NotFoundException();
        }
        if (!hasBaseFunction(BaseFunction.ADMIN) && !principal.getId().equals(existing.getUserId())) {
            throw new ForbiddenClientException();
        }
        dao.deleteByKey(plugin.getId(), collection, key);
        return Response.ok().entity(Json.createObjectBuilder().add("status", "ok").build()).build();
    }

    @GET
    @Path("{id: [a-z0-9\\-]+}/action/{action: [a-zA-Z0-9_\\-]+}")
    public Response invokeGetAction(@PathParam("id") String id,
                                    @PathParam("action") String action,
                                    @Context UriInfo uriInfo) {
        return invokeAction("GET", id, action, uriInfo, null);
    }

    @POST
    @Path("{id: [a-z0-9\\-]+}/action/{action: [a-zA-Z0-9_\\-]+}")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.WILDCARD})
    public Response invokePostAction(@PathParam("id") String id,
                                     @PathParam("action") String action,
                                     @Context UriInfo uriInfo,
                                     String body) {
        return invokeAction("POST", id, action, uriInfo, body);
    }

    @DELETE
    @Path("{id: [a-z0-9\\-]+}/action/{action: [a-zA-Z0-9_\\-]+}")
    public Response invokeDeleteAction(@PathParam("id") String id,
                                       @PathParam("action") String action,
                                       @Context UriInfo uriInfo) {
        return invokeAction("DELETE", id, action, uriInfo, null);
    }

    private Response invokeAction(String method, String id, String action, UriInfo uriInfo, String body) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        Plugin plugin = requireEnabledPlugin(id);
        PluginHandler handler = PluginService.getHandler(plugin.getHandler());
        if (handler == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Json.createObjectBuilder()
                            .add("type", "HandlerNotFound")
                            .add("message", "No backend handler registered for plugin: " + id)
                            .build())
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        Map<String, String> queryMap = new HashMap<>();
        MultivaluedMap<String, String> qp = uriInfo.getQueryParameters();
        for (String k : qp.keySet()) {
            queryMap.put(k, qp.getFirst(k));
        }

        User user = new UserDao().getById(principal.getId());
        String displayName = user != null && !Strings.isNullOrEmpty(user.getName()) ? user.getName() : principal.getName();

        PluginContext ctx = new PluginContext(
                plugin.getId(),
                principal.getId(),
                principal.getName(),
                displayName,
                hasBaseFunction(BaseFunction.ADMIN),
                getTargetIdList(null),
                queryMap,
                body);

        try {
            PluginResult result = handler.handleAction(method, action, ctx);
            if (result.isBinary()) {
                String encodedName = URLEncoder.encode(
                        Strings.isNullOrEmpty(result.getFileName()) ? "export.pdf" : result.getFileName(),
                        StandardCharsets.UTF_8).replace("+", "%20");
                return Response.status(result.getStatusCode())
                        .entity(result.getBinaryData())
                        .type(result.getContentType())
                        .header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName)
                        .build();
            }
            return Response.status(result.getStatusCode())
                    .entity(result.getJson() != null ? result.getJson() : Json.createObjectBuilder().build())
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (ForbiddenClientException | ClientException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException("PluginActionError", "Error executing plugin action: " + e.getMessage(), e);
        }
    }

    private Plugin requireEnabledPlugin(String id) {
        Plugin plugin = new PluginDao().getById(id);
        if (plugin == null) {
            throw new NotFoundException();
        }
        if (!plugin.isEnabled()) {
            throw new ForbiddenClientException();
        }
        return plugin;
    }

    private static String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        return "application/octet-stream";
    }
}
