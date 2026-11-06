package com.sismics.docs.core.dao;

import com.google.common.base.Joiner;
import com.sismics.docs.core.constant.AuditLogType;
import com.sismics.docs.core.dao.criteria.TagCriteria;
import com.sismics.docs.core.dao.dto.TagDto;
import com.sismics.docs.core.model.jpa.DocumentTag;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.util.AuditLogUtil;
import com.sismics.docs.core.util.SecurityUtil;
import com.sismics.docs.core.util.jpa.QueryParam;
import com.sismics.docs.core.util.jpa.QueryUtil;
import com.sismics.docs.core.util.jpa.SortCriteria;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import java.util.*;

/**
 * Tag DAO.
 * 
 * @author bgamard
 */
public class TagDao {
    /**
     * Gets a tag by its ID.
     * 
     * @param id Tag ID
     * @return Tag
     */
    public Tag getById(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            return em.find(Tag.class, id);
        } catch (NoResultException e) {
            return null;
        }
    }
    
    /**
     * Update tags on a document.
     * 
     * @param documentId Document ID
     * @param tagIdSet Set of tag ID
     */
    public void updateTagList(String documentId, Set<String> tagIdSet) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        
        // Get current tag links
        Query q = em.createQuery("select dt from DocumentTag dt where dt.documentId = :documentId and dt.deleteDate is null");
        q.setParameter("documentId", documentId);
        @SuppressWarnings("unchecked")
        List<DocumentTag> documentTagList = q.getResultList();
        
        // Deleting tags no longer linked
        for (DocumentTag documentTag : documentTagList) {
            if (!tagIdSet.contains(documentTag.getTagId())) {
                documentTag.setDeleteDate(new Date());
            }
        }
        
        // Adding new tag links
        for (String tagId : tagIdSet) {
            boolean found = false;
            for (DocumentTag documentTag : documentTagList) {
                if (documentTag.getTagId().equals(tagId)) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                DocumentTag documentTag = new DocumentTag();
                documentTag.setId(UUID.randomUUID().toString());
                documentTag.setDocumentId(documentId);
                documentTag.setTagId(tagId);
                Integer maxOrder = getMaxDocumentOrderByTag(tagId);
                documentTag.setOrder(maxOrder == null ? 0 : maxOrder + 1);
                em.persist(documentTag);
            }
        }
    }
    
    /**
     * Creates a new tag.
     * 
     * @param tag Tag
     * @param userId User ID
     * @return New ID
     */
    public String create(Tag tag, String userId) {
        // Create the UUID
        tag.setId(UUID.randomUUID().toString());
        
        // Compute initial order if not set
        if (tag.getOrder() == null) {
            Integer max = getMaxOrderByParent(tag.getParentId());
            tag.setOrder(max == null ? 0 : max + 1);
        }
        
        // Create the tag
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        tag.setCreateDate(new Date());
        em.persist(tag);
        
        // Create audit log
        AuditLogUtil.create(tag, AuditLogType.CREATE, userId);
        
        return tag.getId();
    }
    
    /**
     * Deletes a tag.
     * 
     * @param tagId Tag ID
     * @param userId User ID
     */
    public void delete(String tagId, String userId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
            
        // Get the tag
        Query q = em.createQuery("select t from Tag t where t.id = :id and t.deleteDate is null");
        q.setParameter("id", tagId);
        Tag tagDb = (Tag) q.getSingleResult();
        
        // Delete the tag
        Date dateNow = new Date();
        tagDb.setDeleteDate(dateNow);

        // Delete linked data
        q = em.createQuery("update DocumentTag dt set dt.deleteDate = :dateNow where dt.tagId = :tagId and dt.deleteDate is null");
        q.setParameter("dateNow", dateNow);
        q.setParameter("tagId", tagId);
        q.executeUpdate();

        q = em.createQuery("update Acl a set a.deleteDate = :dateNow where a.sourceId = :tagId and a.deleteDate is null");
        q.setParameter("tagId", tagId);
        q.setParameter("dateNow", dateNow);
        q.executeUpdate();

        q = em.createQuery("update Tag t set t.parentId = null where t.parentId = :tagId and t.deleteDate is null");
        q.setParameter("tagId", tagId);
        q.executeUpdate();
        
        // Create audit log
        AuditLogUtil.create(tagDb, AuditLogType.DELETE, userId);
    }
    
    /**
     * Update a tag.
     * 
     * @param tag Tag to update
     * @param userId User ID
     * @return Updated tag
     */
    public Tag update(Tag tag, String userId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        
        // Get the tag
        Query q = em.createQuery("select t from Tag t where t.id = :id and t.deleteDate is null");
        q.setParameter("id", tag.getId());
        Tag tagDb = (Tag) q.getSingleResult();
        
        // Update the tag
        tagDb.setName(tag.getName());
        tagDb.setColor(tag.getColor());
        tagDb.setParentId(tag.getParentId());
        tagDb.setOrder(tag.getOrder());
        
        // Create audit log
        AuditLogUtil.create(tagDb, AuditLogType.UPDATE, userId);
        
        return tagDb;
    }

    /**
     * Returns the list of all tags.
     *
     * @param criteria Search criteria
     * @param sortCriteria Sort criteria
     * @return List of groups
     */
    public List<TagDto> findByCriteria(TagCriteria criteria, SortCriteria sortCriteria) {
        Map<String, Object> parameterMap = new HashMap<>();
        List<String> criteriaList = new ArrayList<>();

        StringBuilder sb = new StringBuilder("select distinct t.TAG_ID_C as c0, t.TAG_NAME_C as c1, t.TAG_COLOR_C as c2, t.TAG_IDPARENT_C as c3, u.USE_USERNAME_C as c4, t.TAG_ORDER_N as c5, t.TAG_CREATEDATE_D as c6 ");
        sb.append(" from T_TAG t ");
        sb.append(" join T_USER u on t.TAG_IDUSER_C = u.USE_ID_C ");

        // Add search criterias
        if (criteria.getId() != null) {
            criteriaList.add("t.TAG_ID_C = :id");
            parameterMap.put("id", criteria.getId());
        }
        if (criteria.getTargetIdList() != null && !SecurityUtil.skipAclCheck(criteria.getTargetIdList())) {
            sb.append(" left join T_ACL a on a.ACL_TARGETID_C in (:targetIdList) and a.ACL_SOURCEID_C = t.TAG_ID_C and a.ACL_PERM_C = 'READ' and a.ACL_DELETEDATE_D is null ");
            criteriaList.add("a.ACL_ID_C is not null");
            parameterMap.put("targetIdList", criteria.getTargetIdList());
        }
        if (criteria.getDocumentId() != null) {
            sb.append(" join T_DOCUMENT_TAG dt on dt.DOT_IDTAG_C = t.TAG_ID_C and dt.DOT_DELETEDATE_D is null ");
            criteriaList.add("dt.DOT_IDDOCUMENT_C = :documentId");
            parameterMap.put("documentId", criteria.getDocumentId());
        }

        criteriaList.add("t.TAG_DELETEDATE_D is null");

        sb.append(" where ");
        sb.append(Joiner.on(" and ").join(criteriaList));

        // Perform the search
        QueryParam queryParam;
        if (sortCriteria != null) {
            queryParam = QueryUtil.getSortedQueryParam(new QueryParam(sb.toString(), parameterMap), sortCriteria);
        } else {
            sb.append(" order by coalesce(t.TAG_ORDER_N, 999999999) asc, t.TAG_CREATEDATE_D asc, t.TAG_NAME_C asc");
            queryParam = new QueryParam(sb.toString(), parameterMap);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> l = QueryUtil.getNativeQuery(queryParam).getResultList();

        // Assemble results
        List<TagDto> tagDtoList = new ArrayList<>();
        for (Object[] o : l) {
            int i = 0;
            TagDto tagDto = new TagDto()
                    .setId((String) o[i++])
                    .setName((String) o[i++])
                    .setColor((String) o[i++])
                    .setParentId((String) o[i++])
                    .setCreator((String) o[i++])
                    .setOrder(o[i] == null ? null : ((Number) o[i]).intValue());
            i++;
            tagDto.setCreateDate((Date) o[i]);
            tagDtoList.add(tagDto);
        }

        if (sortCriteria == null) {
            return sortHierarchically(tagDtoList);
        }

        return tagDtoList;
    }

    /**
     * Sorts tags hierarchically. Root tags first (ordered by order ASC, createDate ASC, name ASC),
     * followed immediately by their children in a depth-first traversal.
     *
     * @param tagDtoList List of tags
     * @return Hierarchically sorted list of tags
     */
    public List<TagDto> sortHierarchically(List<TagDto> tagDtoList) {
        if (tagDtoList == null || tagDtoList.size() <= 1) {
            return tagDtoList;
        }

        Comparator<TagDto> siblingComparator = (a, b) -> {
            int orderA = a.getOrder() != null ? a.getOrder() : Integer.MAX_VALUE;
            int orderB = b.getOrder() != null ? b.getOrder() : Integer.MAX_VALUE;
            if (orderA != orderB) {
                return Integer.compare(orderA, orderB);
            }
            Date dateA = a.getCreateDate();
            Date dateB = b.getCreateDate();
            if (dateA != null && dateB != null && !dateA.equals(dateB)) {
                return dateA.compareTo(dateB);
            } else if (dateA != null && dateB == null) {
                return -1;
            } else if (dateA == null && dateB != null) {
                return 1;
            }
            String nameA = a.getName() != null ? a.getName() : "";
            String nameB = b.getName() != null ? b.getName() : "";
            return nameA.compareToIgnoreCase(nameB);
        };

        Set<String> allIds = new HashSet<>();
        for (TagDto dto : tagDtoList) {
            allIds.add(dto.getId());
        }

        List<TagDto> roots = new ArrayList<>();
        Map<String, List<TagDto>> childrenByParent = new HashMap<>();

        for (TagDto dto : tagDtoList) {
            String parentId = dto.getParentId();
            if (parentId == null || !allIds.contains(parentId)) {
                roots.add(dto);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(dto);
            }
        }

        roots.sort(siblingComparator);

        List<TagDto> result = new ArrayList<>(tagDtoList.size());
        Set<String> visited = new HashSet<>();

        for (TagDto root : roots) {
            traverseTagTree(root, childrenByParent, siblingComparator, result, visited);
        }

        // Add any remaining unvisited tags (e.g. loops in detached subgraphs)
        if (result.size() < tagDtoList.size()) {
            List<TagDto> remaining = new ArrayList<>();
            for (TagDto dto : tagDtoList) {
                if (!visited.contains(dto.getId())) {
                    remaining.add(dto);
                }
            }
            remaining.sort(siblingComparator);
            for (TagDto dto : remaining) {
                traverseTagTree(dto, childrenByParent, siblingComparator, result, visited);
            }
        }

        return result;
    }

    private void traverseTagTree(TagDto current, Map<String, List<TagDto>> childrenByParent,
                                 Comparator<TagDto> comparator, List<TagDto> result, Set<String> visited) {
        if (!visited.add(current.getId())) {
            return;
        }
        result.add(current);
        List<TagDto> children = childrenByParent.get(current.getId());
        if (children != null) {
            children.sort(comparator);
            for (TagDto child : children) {
                traverseTagTree(child, childrenByParent, comparator, result, visited);
            }
        }
    }

    /**
     * Returns a map of tag ID to the set of readable active document IDs directly linked to that tag.
     *
     * @param targetIdList List of security targets for ACL check
     * @return Map of tagId to Set of documentId
     */
    public Map<String, Set<String>> getReadableDocumentIdsByTag(List<String> targetIdList) {
        Map<String, Object> parameterMap = new HashMap<>();
        StringBuilder sb = new StringBuilder("select distinct dt.DOT_IDTAG_C, dt.DOT_IDDOCUMENT_C ");
        sb.append(" from T_DOCUMENT_TAG dt ");
        sb.append(" join T_DOCUMENT d on d.DOC_ID_C = dt.DOT_IDDOCUMENT_C and d.DOC_DELETEDATE_D is null ");
        sb.append(" join T_TAG t on t.TAG_ID_C = dt.DOT_IDTAG_C and t.TAG_DELETEDATE_D is null ");

        List<String> criteriaList = new ArrayList<>();
        criteriaList.add("dt.DOT_DELETEDATE_D is null");

        if (targetIdList != null && !SecurityUtil.skipAclCheck(targetIdList)) {
            sb.append(" left join T_ACL a on a.ACL_TARGETID_C in (:targetIdList) and a.ACL_SOURCEID_C = d.DOC_ID_C and a.ACL_PERM_C = 'READ' and a.ACL_DELETEDATE_D is null ");
            sb.append(" left join T_DOCUMENT_TAG dta on dta.DOT_IDDOCUMENT_C = d.DOC_ID_C and dta.DOT_DELETEDATE_D is null ");
            sb.append(" left join T_ACL a2 on a2.ACL_TARGETID_C in (:targetIdList) and a2.ACL_SOURCEID_C = dta.DOT_IDTAG_C and a2.ACL_PERM_C = 'READ' and a2.ACL_DELETEDATE_D is null ");
            criteriaList.add("(a.ACL_ID_C is not null or a2.ACL_ID_C is not null)");
            parameterMap.put("targetIdList", targetIdList);
        }

        sb.append(" where ");
        sb.append(Joiner.on(" and ").join(criteriaList));

        QueryParam queryParam = new QueryParam(sb.toString(), parameterMap);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = QueryUtil.getNativeQuery(queryParam).getResultList();

        Map<String, Set<String>> result = new HashMap<>();
        for (Object[] row : rows) {
            String tagId = (String) row[0];
            String docId = (String) row[1];
            result.computeIfAbsent(tagId, k -> new HashSet<>()).add(docId);
        }
        return result;
    }

    /**
     * Gets the maximum order number for tags in a given hierarchy level.
     *
     * @param parentId Parent tag ID, or null for root tags
     * @return Maximum order number, or null if no tags exist
     */
    public Integer getMaxOrderByParent(String parentId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        StringBuilder sb = new StringBuilder("select max(t.order) from Tag t where t.deleteDate is null ");
        if (parentId == null) {
            sb.append("and t.parentId is null");
        } else {
            sb.append("and t.parentId = :parentId");
        }
        Query q = em.createQuery(sb.toString());
        if (parentId != null) {
            q.setParameter("parentId", parentId);
        }
        try {
            return (Integer) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Updates the order of a tag.
     *
     * @param tagId Tag ID
     * @param order New order
     */
    public void updateOrder(String tagId, int order) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createQuery("update Tag t set t.order = :order where t.id = :tagId and t.deleteDate is null");
        q.setParameter("order", order);
        q.setParameter("tagId", tagId);
        q.executeUpdate();
    }

    /**
     * Initializes tag orders for existing tags where order is null.
     * Orders them sequentially within each parent level based on creation date ASC.
     */
    public void initializeTagOrdersIfNeeded() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query countQuery = em.createQuery("select count(t) from Tag t where t.order is null and t.deleteDate is null");
        Long nullCount = (Long) countQuery.getSingleResult();
        if (nullCount == null || nullCount == 0L) {
            return;
        }

        // Get all active tags
        Query q = em.createQuery("select t from Tag t where t.deleteDate is null order by t.createDate asc, t.id asc");
        @SuppressWarnings("unchecked")
        List<Tag> allTags = q.getResultList();

        // Group by parentId
        Map<String, List<Tag>> grouped = new HashMap<>();
        for (Tag t : allTags) {
            String p = t.getParentId() == null ? "" : t.getParentId();
            grouped.computeIfAbsent(p, k -> new ArrayList<>()).add(t);
        }

        for (List<Tag> list : grouped.values()) {
            for (int i = 0; i < list.size(); i++) {
                Tag t = list.get(i);
                if (t.getOrder() == null) {
                    t.setOrder(i);
                }
            }
        }
    }
    
    /**
     * Gets the maximum order number for documents within a tag.
     *
     * @param tagId Tag ID
     * @return Maximum order number, or null if no documents exist
     */
    public Integer getMaxDocumentOrderByTag(String tagId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createQuery("select max(dt.order) from DocumentTag dt, Document d where dt.documentId = d.id and d.deleteDate is null and dt.tagId = :tagId and dt.deleteDate is null");
        q.setParameter("tagId", tagId);
        try {
            return (Integer) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Cleans ghost/orphan DocumentTag records where the document is deleted.
     *
     * @param tagId Tag ID
     */
    public void cleanGhostDocumentTags(String tagId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Date now = new Date();
        Query q = em.createQuery("update DocumentTag dt set dt.deleteDate = :now where dt.deleteDate is null and dt.tagId = :tagId and dt.documentId in (select d.id from Document d where d.deleteDate is not null)");
        q.setParameter("now", now);
        q.setParameter("tagId", tagId);
        q.executeUpdate();
    }

    /**
     * Reorders documents within a tag, auto-compacting active documents into consecutive orders.
     *
     * @param tagId Tag ID
     * @param orderedDocIds List of document IDs in their new order
     * @param offset Optional page offset
     */
    public void reorderDocumentsInTag(String tagId, List<String> orderedDocIds, Integer offset) {
        if (orderedDocIds == null || orderedDocIds.isEmpty()) {
            return;
        }

        EntityManager em = ThreadLocalContext.get().getEntityManager();
        cleanGhostDocumentTags(tagId);

        // Fetch all active DocumentTag records for this tag
        Query q = em.createQuery("select dt from DocumentTag dt, Document d where dt.documentId = d.id and d.deleteDate is null and dt.tagId = :tagId and dt.deleteDate is null order by coalesce(dt.order, 999999999) asc, d.title asc");
        q.setParameter("tagId", tagId);
        @SuppressWarnings("unchecked")
        List<DocumentTag> allActive = q.getResultList();

        Map<String, DocumentTag> map = new HashMap<>();
        for (DocumentTag dt : allActive) {
            map.put(dt.getDocumentId(), dt);
        }

        Set<String> reorderedSet = new HashSet<>(orderedDocIds);
        int startOffset = (offset != null && offset >= 0) ? offset : 0;
        if (startOffset > allActive.size()) {
            startOffset = allActive.size();
        }

        List<DocumentTag> before = new ArrayList<>();
        List<DocumentTag> current = new ArrayList<>();
        List<DocumentTag> after = new ArrayList<>();

        for (int i = 0; i < allActive.size(); i++) {
            DocumentTag dt = allActive.get(i);
            if (reorderedSet.contains(dt.getDocumentId())) {
                continue; // Handled in current
            }
            if (i < startOffset) {
                before.add(dt);
            } else {
                after.add(dt);
            }
        }

        for (String docId : orderedDocIds) {
            DocumentTag dt = map.get(docId);
            if (dt != null) {
                current.add(dt);
            }
        }

        List<DocumentTag> finalOrder = new ArrayList<>(allActive.size());
        finalOrder.addAll(before);
        finalOrder.addAll(current);
        finalOrder.addAll(after);

        // Assign clean consecutive 0, 1, 2... orders
        for (int i = 0; i < finalOrder.size(); i++) {
            finalOrder.get(i).setOrder(i);
        }
    }
}
