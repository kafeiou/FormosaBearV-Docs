package com.sismics.docs.core.dao;

import com.sismics.docs.core.model.jpa.Share;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Share DAO.
 * 
 * @author bgamard
 */
public class ShareDao {
    /**
     * Summary projection of an active share linked to an active document.
     */
    public static class ShareItem {
        private final String id;
        private final String name;
        private final Date createDate;
        private final boolean allowExport;
        private final String documentId;
        private final String documentTitle;
        private final String ownerUsername;

        public ShareItem(String id, String name, Date createDate, boolean allowExport,
                         String documentId, String documentTitle, String ownerUsername) {
            this.id = id;
            this.name = name;
            this.createDate = createDate;
            this.allowExport = allowExport;
            this.documentId = documentId;
            this.documentTitle = documentTitle;
            this.ownerUsername = ownerUsername;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Date getCreateDate() { return createDate; }
        public boolean isAllowExport() { return allowExport; }
        public String getDocumentId() { return documentId; }
        public String getDocumentTitle() { return documentTitle; }
        public String getOwnerUsername() { return ownerUsername; }
    }
    /**
     * Creates a new share.
     * 
     * @param share Share
     * @return New ID
     */
    public String create(Share share) {
        // Create the UUID
        share.setId(UUID.randomUUID().toString());
        
        // Create the share
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        share.setCreateDate(new Date());
        em.persist(share);
        
        return share.getId();
    }

    /**
     * Gets an active share by its ID.
     *
     * @param id Share ID
     * @return Share or null if not found
     */
    public Share getActiveById(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createQuery("select s from Share s where s.id = :id and s.deleteDate is null");
        q.setParameter("id", id);
        @SuppressWarnings("unchecked")
        java.util.List<Share> list = q.getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Updates a share.
     *
     * @param share Share to update
     * @return Updated share
     */
    public Share update(Share share) {
        Share shareDb = getActiveById(share.getId());
        if (shareDb != null) {
            shareDb.setName(share.getName());
            shareDb.setAllowExport(share.isAllowExport());
        }
        return shareDb;
    }
    
    /**
     * Deletes a share.
     * 
     * @param id Share ID
     */
    public void delete(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
            
        // Get the share
        Query q = em.createQuery("select s from Share s where s.id = :id and s.deleteDate is null");
        q.setParameter("id", id);
        Share shareDb = (Share) q.getSingleResult();
        
        // Delete the share
        Date dateNow = new Date();
        shareDb.setDeleteDate(dateNow);
        
        // Delete the linked ACL
        q = em.createQuery("update Acl a set a.deleteDate = :dateNow where a.targetId = :targetId");
        q.setParameter("targetId", id);
        q.setParameter("dateNow", dateNow);
        q.executeUpdate();
    }

    /**
     * Lists all active shares linked to non-deleted documents, newest first.
     *
     * @return List of active share items
     */
    @SuppressWarnings("unchecked")
    public List<ShareItem> findAllActive() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createNativeQuery(
                "select s.SHA_ID_C, s.SHA_NAME_C, s.SHA_CREATEDATE_D, s.SHA_ALLOWEXPORT_B, " +
                "d.DOC_ID_C, d.DOC_TITLE_C, u.USE_USERNAME_C " +
                "from T_SHARE s " +
                "join T_ACL a on a.ACL_TARGETID_C = s.SHA_ID_C and a.ACL_DELETEDATE_D is null " +
                "join T_DOCUMENT d on d.DOC_ID_C = a.ACL_SOURCEID_C and d.DOC_DELETEDATE_D is null " +
                "join T_USER u on u.USE_ID_C = d.DOC_IDUSER_C " +
                "where s.SHA_DELETEDATE_D is null " +
                "order by s.SHA_CREATEDATE_D desc");
        List<Object[]> rows = q.getResultList();
        List<ShareItem> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new ShareItem(
                    (String) row[0],
                    (String) row[1],
                    (Date) row[2],
                    row[3] != null && (Boolean) row[3],
                    (String) row[4],
                    (String) row[5],
                    (String) row[6]
            ));
        }
        return result;
    }
}
