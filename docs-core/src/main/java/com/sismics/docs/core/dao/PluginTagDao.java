package com.sismics.docs.core.dao;

import com.sismics.docs.core.model.jpa.PluginTag;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.UUID;

/**
 * Plugin protected tag DAO.
 */
public class PluginTagDao {
    public String create(PluginTag pluginTag) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        if (pluginTag.getId() == null) {
            pluginTag.setId(UUID.randomUUID().toString());
        }
        em.persist(pluginTag);
        return pluginTag.getId();
    }

    public List<PluginTag> findByPluginId(String pluginId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<PluginTag> q = em.createQuery(
                "select pt from PluginTag pt where pt.pluginId = :pluginId", PluginTag.class);
        q.setParameter("pluginId", pluginId);
        return q.getResultList();
    }

    public PluginTag findByPluginIdAndRole(String pluginId, String role) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<PluginTag> q = em.createQuery(
                "select pt from PluginTag pt where pt.pluginId = :pluginId and pt.role = :role", PluginTag.class);
        q.setParameter("pluginId", pluginId);
        q.setParameter("role", role);
        List<PluginTag> list = q.getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isTagProtected(String tagId) {
        if (tagId == null) {
            return false;
        }
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<Long> q = em.createQuery(
                "select count(pt) from PluginTag pt where pt.tagId = :tagId", Long.class);
        q.setParameter("tagId", tagId);
        return q.getSingleResult() > 0;
    }

    public boolean isManagementProtectedTag(String tagId) {
        if (tagId == null) {
            return false;
        }
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<Long> q = em.createQuery(
                "select count(pt) from PluginTag pt where pt.tagId = :tagId and pt.role = 'MANAGEMENT'", Long.class);
        q.setParameter("tagId", tagId);
        return q.getSingleResult() > 0;
    }

    public void deleteByPluginId(String pluginId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        em.createQuery("delete from PluginTag pt where pt.pluginId = :pluginId")
                .setParameter("pluginId", pluginId)
                .executeUpdate();
    }
}
