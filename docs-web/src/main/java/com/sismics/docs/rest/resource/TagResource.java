package com.sismics.docs.rest.resource;

import com.google.common.collect.Sets;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.criteria.TagCriteria;
import com.sismics.docs.core.dao.dto.AclDto;
import com.sismics.docs.core.dao.dto.TagDto;
import com.sismics.docs.core.model.jpa.Acl;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.service.TagReportService;
import com.sismics.docs.core.util.jpa.SortCriteria;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.util.AclUtil;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.util.JsonUtil;
import org.apache.commons.lang3.StringUtils;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tag REST resources.
 * 
 * @author bgamard
 */
@Path("/tag")
public class TagResource extends BaseResource {
    /**
     * Returns the list of all visible tags.
     *
     * @api {get} /tag/list Get tags
     * @apiName GetTagList
     * @apiGroup Tag
     * @apiSuccess {Object[]} tags List of tags
     * @apiSuccess {String} tags.id ID
     * @apiSuccess {String} tags.name Name
     * @apiSuccess {String} tags.color Color
     * @apiSuccess {String} tags.parent Parent
     * @apiError (client) ForbiddenError Access denied
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @return Response
     */
    @GET
    @Path("/list")
    public Response list() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        TagDao tagDao = new TagDao();
        List<String> targetIdList = getTargetIdList(null);
        List<TagDto> tagDtoList = tagDao.findByCriteria(new TagCriteria().setTargetIdList(targetIdList), null);
        Map<String, Set<String>> directDocsByTag = tagDao.getReadableDocumentIdsByTag(targetIdList);

        // Extract tag IDs
        Set<String> tagIdSet = Sets.newHashSet();
        for (TagDto tagDto : tagDtoList) {
            tagIdSet.add(tagDto.getId());
        }

        // Build the response
        JsonArrayBuilder items = Json.createArrayBuilder();
        for (TagDto tagDto : tagDtoList) {
            Set<String> totalDocs = Sets.newHashSet();
            Set<String> visitedTags = Sets.newHashSet();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(tagDto.getId());
            while (!stack.isEmpty()) {
                String currentTagId = stack.pop();
                if (!visitedTags.add(currentTagId)) {
                    continue;
                }
                Set<String> directDocs = directDocsByTag.get(currentTagId);
                if (directDocs != null) {
                    totalDocs.addAll(directDocs);
                }
                for (TagDto childCandidate : tagDtoList) {
                    if (currentTagId.equals(childCandidate.getParentId())) {
                        stack.push(childCandidate.getId());
                    }
                }
            }

            JsonObjectBuilder item = Json.createObjectBuilder()
                    .add("id", tagDto.getId())
                    .add("name", tagDto.getName())
                    .add("color", tagDto.getColor())
                    .add("document_count", tagDto.isContainer() ? 0 : totalDocs.size())
                    .add("inherit_acl", tagDto.isInheritAcl())
                    .add("is_container", tagDto.isContainer());
            if (tagDto.getOrder() != null) {
                item.add("order", tagDto.getOrder());
            }
            if (tagDto.getCreateDate() != null) {
                item.add("create_date", tagDto.getCreateDate().getTime());
            }
            if (tagIdSet.contains(tagDto.getParentId())) {
                item.add("parent", tagDto.getParentId());
            }
            if (tagDto.getCreator() != null) {
                item.add("creator", tagDto.getCreator());
            }
            items.add(item);
        }
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("tags", items);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns a tag.
     *
     * @api {get} /tag/:id Get a tag
     * @apiName GetTag
     * @apiGroup Tag
     * @apiSuccess {String} id ID
     * @apiSuccess {String} name Name
     * @apiSuccess {String} creator Username of the creator
     * @apiSuccess {String} color Color
     * @apiSuccess {String} parent Parent
     * @apiSuccess {Boolean} writable True if the tag is writable by the current user
     * @apiSuccess {Object[]} acls List of ACL
     * @apiSuccess {String} acls.id ID
     * @apiSuccess {String="READ","WRITE"} acls.perm Permission
     * @apiSuccess {String} acls.name Target name
     * @apiSuccess {String="USER","GROUP","SHARE"} acls.type Target type
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Tag not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id Tag ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}")
    public Response get(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        TagDao tagDao = new TagDao();
        List<TagDto> tagDtoList = tagDao.findByCriteria(new TagCriteria().setTargetIdList(getTargetIdList(null)).setId(id), null);
        if (tagDtoList.isEmpty()) {
            throw new NotFoundException();
        }

        // Add tag informatiosn
        TagDto tagDto = tagDtoList.get(0);
        JsonObjectBuilder tag = Json.createObjectBuilder()
                .add("id", tagDto.getId())
                .add("creator", tagDto.getCreator())
                .add("name", tagDto.getName())
                .add("color", tagDto.getColor())
                .add("inherit_acl", tagDto.isInheritAcl())
                .add("is_container", tagDto.isContainer());
        if (tagDto.getOrder() != null) {
            tag.add("order", tagDto.getOrder());
        }
        if (tagDto.getCreateDate() != null) {
            tag.add("create_date", tagDto.getCreateDate().getTime());
        }

        // Add the parent if its visible
        AclDao aclDao = new AclDao();
        if (tagDto.getParentId() != null) {
            if (aclDao.checkPermission(tagDto.getParentId(), PermType.READ, getTargetIdList(null))) {
                tag.add("parent", tagDto.getParentId());
            }
        }

        // Add ACL
        if (tagDto.isContainer()) {
            tag.add("acls", Json.createArrayBuilder())
                    .add("writable", false);
            tag.add("inherited_acls", Json.createArrayBuilder());
        } else {
            AclUtil.addAcls(tag, id, getTargetIdList(null));

            // Add inherited ACLs from effective ancestor tags
            JsonArrayBuilder inheritedAclArray = Json.createArrayBuilder();
            if (tagDto.isInheritAcl()) {
                List<String> effectiveTagIds = tagDao.getEffectiveAclTagIds(id);
                for (int i = 1; i < effectiveTagIds.size(); i++) {
                    String ancestorId = effectiveTagIds.get(i);
                    Tag ancestorTag = tagDao.getById(ancestorId);
                    String ancestorName = ancestorTag != null ? ancestorTag.getName() : ancestorId;
                    List<AclDto> ancestorAcls = aclDao.getBySourceId(ancestorId, AclType.USER);
                    for (AclDto aclDto : ancestorAcls) {
                        inheritedAclArray.add(Json.createObjectBuilder()
                                .add("perm", aclDto.getPerm().name())
                                .add("id", aclDto.getTargetId())
                                .add("name", JsonUtil.nullable(aclDto.getTargetName()))
                                .add("type", aclDto.getTargetType())
                                .add("source_tag_id", ancestorId)
                                .add("source_tag_name", ancestorName));
                    }
                }
            }
            tag.add("inherited_acls", inheritedAclArray);
        }

        return Response.ok().entity(tag.build()).build();
    }

    /**
     * Creates a new tag.
     *
     * @api {put} /tag Create a tag
     * @apiName PutTag
     * @apiGroup Tag
     * @apiParam {String} name Name
     * @apiParam {String} color Color
     * @apiParam {String} parent Parent ID
     * @apiSuccess {String} id Tag ID
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) IllegalTagName Spaces and colons are not allowed in tag name
     * @apiError (client) ParentNotFound Parent not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param name Name
     * @param color Color
     * @param parentId Parent ID
     * @return Response
     */
    @PUT
    public Response add(
            @FormParam("name") String name,
            @FormParam("color") String color,
            @FormParam("parent") String parentId,
            @FormParam("order") Integer order,
            @FormParam("inherit_acl") Boolean inheritAcl) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        name = ValidationUtil.validateLength(name, "name", 1, 36, false);
        ValidationUtil.validateHexColor(color, "color", true);
        ValidationUtil.validateTagName(name);

        // Check the parent
        if (StringUtils.isEmpty(parentId)) {
            parentId = null;
        } else {
            AclDao aclDao = new AclDao();
            if (!aclDao.checkPermission(parentId, PermType.READ, getTargetIdList(null))) {
                throw new ClientException("ParentNotFound", MessageFormat.format("Parent not found: {0}", parentId));
            }
        }

        // Create the tag
        TagDao tagDao = new TagDao();
        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(color);
        tag.setUserId(principal.getId());
        tag.setParentId(parentId);
        tag.setInheritAcl(inheritAcl != null ? inheritAcl : true);
        if (order != null) {
            tag.setOrder(order);
        }
        String id = tagDao.create(tag, principal.getId());

        // Create read ACL
        AclDao aclDao = new AclDao();
        Acl acl = new Acl();
        acl.setPerm(PermType.READ);
        acl.setType(AclType.USER);
        acl.setSourceId(id);
        acl.setTargetId(principal.getId());
        aclDao.create(acl, principal.getId());

        // Create write ACL
        acl = new Acl();
        acl.setPerm(PermType.WRITE);
        acl.setType(AclType.USER);
        acl.setSourceId(id);
        acl.setTargetId(principal.getId());
        aclDao.create(acl, principal.getId());
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("id", id);
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Update a tag.
     *
     * @api {post} /tag/:id Update a tag
     * @apiName PostTag
     * @apiGroup Tag
     * @apiParam {String} id Tag ID
     * @apiParam {String} name Name
     * @apiParam {String} color Color
     * @apiParam {String} parent Parent ID
     * @apiSuccess {String} id Tag ID
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) IllegalTagName Spaces and colons are not allowed in tag name
     * @apiError (client) ParentNotFound Parent not found
     * @apiError (client) CircularReference Circular reference in parent tag
     * @apiError (client) NotFound Tag not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param name Name
     * @param color Color
     * @param parentId Parent ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}")
    public Response update(
            @PathParam("id") String id,
            @FormParam("name") String name,
            @FormParam("color") String color,
            @FormParam("parent") String parentId,
            @FormParam("order") Integer order,
            @FormParam("inherit_acl") Boolean inheritAcl) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        name = ValidationUtil.validateLength(name, "name", 1, 36, true);
        ValidationUtil.validateHexColor(color, "color", true);
        if (name != null) {
            ValidationUtil.validateTagName(name);
        }

        // Check permission
        AclDao aclDao = new AclDao();
        if (!aclDao.checkPermission(id, PermType.WRITE, getTargetIdList(null))) {
            throw new NotFoundException();
        }
        
        // Check the parent
        TagDao tagDao = new TagDao();
        if (StringUtils.isEmpty(parentId)) {
            parentId = null;
        } else {
            if (!aclDao.checkPermission(parentId, PermType.READ, getTargetIdList(null))) {
                throw new ClientException("ParentNotFound", MessageFormat.format("Parent not found: {0}", parentId));
            }

            String parentTagId = parentId;
            do {
                Tag parentTag = tagDao.getById(parentTagId);
                parentTagId = parentTag.getParentId();
                if (parentTag.getId().equals(id)) {
                    throw new ClientException("CircularReference", "Circular reference in parent tag");
                }
            } while (parentTagId != null);
        }

        // Update the tag
        Tag tag = tagDao.getById(id);
        if (!StringUtils.isEmpty(name)) {
            tag.setName(name);
        }
        if (!StringUtils.isEmpty(color)) {
            tag.setColor(color);
        }
        // Handle parent change and order
        boolean parentChanged = (parentId == null && tag.getParentId() != null)
                || (parentId != null && !parentId.equals(tag.getParentId()));
        if (parentChanged) {
            Integer maxOrder = tagDao.getMaxOrderByParent(parentId);
            tag.setOrder(maxOrder == null ? 0 : maxOrder + 1);
        } else if (order != null) {
            tag.setOrder(order);
        }
        // Parent tag is always updated to have the possibility to delete it
        tag.setParentId(parentId);

        // Handle inherit_acl transition
        if (inheritAcl != null && inheritAcl != tag.isInheritAcl()) {
            if (!inheritAcl) {
                // Breaking inheritance: copy effective ACLs from parent to this tag as direct ACLs
                String effParentId = parentChanged ? parentId : tag.getParentId();
                if (effParentId != null) {
                    List<String> effectiveAncestorIds = tagDao.getEffectiveAclTagIds(effParentId);
                    List<AclDto> existingAcls = aclDao.getBySourceId(id, AclType.USER);
                    Set<String> existingKeys = new HashSet<>();
                    for (AclDto a : existingAcls) {
                        existingKeys.add(a.getTargetId() + ":" + a.getPerm().name());
                    }
                    for (String ancestorId : effectiveAncestorIds) {
                        List<AclDto> ancestorAcls = aclDao.getBySourceId(ancestorId, AclType.USER);
                        for (AclDto parentAcl : ancestorAcls) {
                            String key = parentAcl.getTargetId() + ":" + parentAcl.getPerm().name();
                            if (!existingKeys.contains(key)) {
                                Acl newAcl = new Acl();
                                newAcl.setSourceId(id);
                                newAcl.setTargetId(parentAcl.getTargetId());
                                newAcl.setPerm(parentAcl.getPerm());
                                newAcl.setType(AclType.USER);
                                aclDao.create(newAcl, principal.getId());
                                existingKeys.add(key);
                            }
                        }
                    }
                }
            } else {
                // Restoring inheritance: clear local custom ACLs on this tag
                List<AclDto> existingAcls = aclDao.getBySourceId(id, AclType.USER);
                for (AclDto a : existingAcls) {
                    aclDao.delete(id, a.getPerm(), a.getTargetId(), principal.getId(), AclType.USER);
                }
            }
            tag.setInheritAcl(inheritAcl);
        }
        
        tagDao.update(tag, principal.getId());
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("id", id);
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Delete a tag.
     *
     * @api {delete} /tag/:id Delete a tag
     * @apiName DeleteTag
     * @apiGroup Tag
     * @apiParam {String} id Tag ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Tag not found
     * @apiPermission user
     * @apiVersion 1.5.0
     * 
     * @param id Tag ID
     * @return Response
     */
    @DELETE
    @Path("{id: [a-z0-9\\-]+}")
    public Response delete(
            @PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the tag
        AclDao aclDao = new AclDao();
        if (!aclDao.checkPermission(id, PermType.WRITE, getTargetIdList(null))) {
            throw new NotFoundException();
        }

        // Delete the tag
        TagDao tagDao = new TagDao();
        if (tagDao.hasChildren(id)) {
            throw new ClientException("TagHasChildren", "Cannot delete a tag that has child tags");
        }
        tagDao.delete(id, principal.getId());
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Reorder tags in a hierarchy level.
     *
     * @api {post} /tag/reorder Reorder tags
     * @apiName ReorderTags
     * @apiGroup Tag
     * @apiParam {String[]} tag_ids Ordered list of tag IDs
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiPermission user
     * @apiVersion 2.0.2
     *
     * @param tagIdList Ordered list of tag IDs
     * @param tagIdsCsv Optional comma-separated list of tag IDs
     * @return Response
     */
    @POST
    @Path("/reorder")
    public Response reorder(
            @FormParam("tag_ids") java.util.List<String> tagIdList,
            @FormParam("tag_ids_csv") String tagIdsCsv) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        java.util.List<String> idList = new java.util.ArrayList<>();
        if (tagIdList != null) {
            for (String item : tagIdList) {
                if (item != null) {
                    if (item.contains(",")) {
                        for (String part : item.split(",")) {
                            if (!part.trim().isEmpty()) {
                                idList.add(part.trim());
                            }
                        }
                    } else if (!item.trim().isEmpty()) {
                        idList.add(item.trim());
                    }
                }
            }
        }
        if (idList.isEmpty() && !StringUtils.isEmpty(tagIdsCsv)) {
            for (String part : tagIdsCsv.split(",")) {
                if (!part.trim().isEmpty()) {
                    idList.add(part.trim());
                }
            }
        }

        if (idList.isEmpty()) {
            JsonObjectBuilder response = Json.createObjectBuilder().add("status", "ok");
            return Response.ok().entity(response.build()).build();
        }

        AclDao aclDao = new AclDao();
        TagDao tagDao = new TagDao();
        java.util.List<String> targetIdList = getTargetIdList(null);

        // Validate all tags exist and user has WRITE permission
        String expectedParentId = null;
        boolean first = true;
        for (String tagId : idList) {
            if (!aclDao.checkPermission(tagId, PermType.WRITE, targetIdList)) {
                throw new ForbiddenClientException();
            }
            Tag tag = tagDao.getById(tagId);
            if (tag == null || tag.getDeleteDate() != null) {
                throw new ClientException("TagNotFound", "Tag not found: " + tagId);
            }
            if (first) {
                expectedParentId = tag.getParentId();
                first = false;
            } else {
                String p = tag.getParentId();
                if ((expectedParentId == null && p != null) || (expectedParentId != null && !expectedParentId.equals(p))) {
                    throw new ClientException("InvalidHierarchy", "All reordered tags must share the same parent");
                }
            }
        }

        // Apply new order
        for (int i = 0; i < idList.size(); i++) {
            tagDao.updateOrder(idList.get(i), i);
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Reorder documents within a tag.
     *
     * @api {post} /tag/:id/document/reorder Reorder documents in tag
     * @apiName ReorderDocumentsInTag
     * @apiGroup Tag
     * @apiParam {String} id Tag ID
     * @apiParam {String[]} document_ids Ordered list of document IDs
     * @apiParam {String} document_ids_csv Optional comma-separated list of document IDs
     * @apiParam {Number} offset Optional pagination offset
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Tag not found
     * @apiPermission user
     * @apiVersion 2.0.2
     *
     * @param id Tag ID
     * @param documentIdList Ordered list of document IDs
     * @param documentIdsCsv Optional comma-separated list of document IDs
     * @param offset Optional page offset
     * @return Response
     */
    @POST
    @Path("/{id: [a-z0-9\\-]+}/document/reorder")
    public Response reorderDocuments(
            @PathParam("id") String id,
            @FormParam("document_ids") java.util.List<String> documentIdList,
            @FormParam("document_ids_csv") String documentIdsCsv,
            @FormParam("offset") Integer offset) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate tag exists and caller has WRITE permission
        TagDao tagDao = new TagDao();
        Tag tag = tagDao.getById(id);
        if (tag == null || tag.getDeleteDate() != null) {
            throw new NotFoundException();
        }

        AclDao aclDao = new AclDao();
        if (!aclDao.checkPermission(id, PermType.WRITE, getTargetIdList(null))) {
            throw new ForbiddenClientException();
        }

        java.util.List<String> idList = new java.util.ArrayList<>();
        if (documentIdList != null) {
            for (String item : documentIdList) {
                if (item != null) {
                    if (item.contains(",")) {
                        for (String part : item.split(",")) {
                            if (!part.trim().isEmpty()) {
                                idList.add(part.trim());
                            }
                        }
                    } else if (!item.trim().isEmpty()) {
                        idList.add(item.trim());
                    }
                }
            }
        }
        if (idList.isEmpty() && !StringUtils.isEmpty(documentIdsCsv)) {
            for (String part : documentIdsCsv.split(",")) {
                if (!part.trim().isEmpty()) {
                    idList.add(part.trim());
                }
            }
        }

        if (!idList.isEmpty()) {
            tagDao.reorderDocumentsInTag(id, idList, offset);
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Exports a hierarchical tag report in JSON, Excel (.xlsx), HTML, or PDF format.
     *
     * @api {get} /tag/export Export tag report
     * @apiName GetTagExport
     * @apiGroup Tag
     * @apiParam {String[]} tag_id List of tag IDs to export
     * @apiParam {String="json","xlsx","html","pdf"} [format="pdf"] Export format
     * @apiParam {Boolean} [include_children=true] Include child tags
     * @apiParam {Boolean} [include_date=true] Include document/file dates in visual reports
     * @apiParam {Boolean} [include_description=true] Include document descriptions in visual reports
     * @apiParam {String="preview","download"} [link_mode="preview"] File hyperlink mode
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @return Response
     */
    @GET
    @Path("/export")
    public Response export(
            @QueryParam("tag_id") List<String> tagIdList,
            @QueryParam("format") @DefaultValue("pdf") String format,
            @QueryParam("include_children") @DefaultValue("true") boolean includeChildren,
            @QueryParam("include_date") @DefaultValue("true") boolean includeDate,
            @QueryParam("include_description") @DefaultValue("true") boolean includeDescription,
            @QueryParam("include_size") @DefaultValue("false") boolean includeSize,
            @QueryParam("link_mode") @DefaultValue("preview") String linkMode,
            @QueryParam("lang") @DefaultValue("zh_TW") String lang,
            @QueryParam("base_url") String baseUrlParam) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        List<String> selectedIds = new java.util.ArrayList<>();
        if (tagIdList != null) {
            for (String item : tagIdList) {
                if (item != null) {
                    for (String part : item.split(",")) {
                        if (!part.trim().isEmpty()) {
                            selectedIds.add(part.trim());
                        }
                    }
                }
            }
        }
        if (selectedIds.isEmpty()) {
            throw new ClientException("ValidationError", "tag_id is required");
        }

        String normalizedFormat = format != null ? format.trim().toLowerCase(java.util.Locale.ROOT) : "pdf";
        if (!"json".equals(normalizedFormat) && !"xlsx".equals(normalizedFormat)
                && !"html".equals(normalizedFormat) && !"pdf".equals(normalizedFormat)) {
            throw new ClientException("ValidationError", "Unsupported format: " + format);
        }

        String resolvedBaseUrl = baseUrlParam;
        if (StringUtils.isBlank(resolvedBaseUrl) && request != null) {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int port = request.getServerPort();
            String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443)
                    || port <= 0;
            resolvedBaseUrl = scheme + "://" + serverName + (defaultPort ? "" : ":" + port) + contextPath;
        }

        TagReportService service = new TagReportService();
        TagReportService.ExportOptions options = new TagReportService.ExportOptions()
                .setSelectedTagIds(selectedIds)
                .setIncludeChildren(includeChildren)
                .setIncludeDate(includeDate)
                .setIncludeDescription(includeDescription)
                .setIncludeSize(includeSize)
                .setLinkMode(linkMode)
                .setLang(lang)
                .setBaseUrl(resolvedBaseUrl)
                .setUserId(principal.getId())
                .setExportedBy(principal.getName())
                .setTargetIdList(getTargetIdList(null))
                .setAdminOrPrivileged(hasBaseFunction(BaseFunction.ADMIN));

        TagReportService.ReportData reportData = service.buildReportData(options);
        if (reportData.getFlatTags().isEmpty()) {
            throw new ForbiddenClientException();
        }

        String dateStamp = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(new java.util.Date());
        String contentType;
        String fileName;
        switch (normalizedFormat) {
            case "json":
                contentType = "application/zip";
                fileName = "tag-report-" + dateStamp + ".zip";
                break;
            case "xlsx":
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                fileName = "tag-report-" + dateStamp + ".xlsx";
                break;
            case "html":
                contentType = "text/html; charset=UTF-8";
                fileName = "tag-report-" + dateStamp + ".html";
                break;
            case "pdf":
            default:
                contentType = "application/pdf";
                fileName = "tag-report-" + dateStamp + ".pdf";
                break;
        }

        jakarta.ws.rs.core.StreamingOutput stream = output -> {
            try {
                switch (normalizedFormat) {
                    case "json":
                        service.exportJsonZip(reportData, output, "tag-report-" + dateStamp + ".json");
                        break;
                    case "xlsx":
                        service.exportExcel(reportData, output);
                        break;
                    case "html":
                        service.exportHtml(reportData, output);
                        break;
                    case "pdf":
                    default:
                        service.exportPdf(reportData, output);
                        break;
                }
            } catch (Exception e) {
                throw new WebApplicationException(e);
            }
        };

        return Response.ok(stream)
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .build();
    }
}
