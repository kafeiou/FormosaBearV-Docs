package com.sismics.docs.core.dao;

import com.sismics.docs.core.model.jpa.PluginData;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Generic plugin JSON storage DAO.
 */
public class PluginDataDao {
    public PluginData getByKey(String pluginId, String collection, String key) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<PluginData> q = em.createQuery(
                "select pd from PluginData pd where pd.pluginId = :pluginId and pd.collection = :collection and pd.key = :key",
                PluginData.class);
        q.setParameter("pluginId", pluginId);
        q.setParameter("collection", collection);
        q.setParameter("key", key);
        List<PluginData> list = q.getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public List<PluginData> findByCollection(String pluginId, String collection) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<PluginData> q = em.createQuery(
                "select pd from PluginData pd where pd.pluginId = :pluginId and pd.collection = :collection order by pd.updateDate desc",
                PluginData.class);
        q.setParameter("pluginId", pluginId);
        q.setParameter("collection", collection);
        return q.getResultList();
    }

    public List<PluginData> findByCollectionAndUser(String pluginId, String collection, String userId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<PluginData> q = em.createQuery(
                "select pd from PluginData pd where pd.pluginId = :pluginId and pd.collection = :collection and pd.userId = :userId order by pd.updateDate desc",
                PluginData.class);
        q.setParameter("pluginId", pluginId);
        q.setParameter("collection", collection);
        q.setParameter("userId", userId);
        return q.getResultList();
    }

    public PluginData upsert(String pluginId, String collection, String key, String userId, String value) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Date now = new Date();
        PluginData existing = getByKey(pluginId, collection, key);
        if (existing == null) {
            PluginData pd = new PluginData();
            pd.setId(UUID.randomUUID().toString());
            pd.setPluginId(pluginId);
            pd.setCollection(collection);
            pd.setKey(key);
            pd.setUserId(userId);
            pd.setValue(value);
            pd.setCreateDate(now);
            pd.setUpdateDate(now);
            em.persist(pd);
            return pd;
        }
        existing.setUserId(userId);
        existing.setValue(value);
        existing.setUpdateDate(now);
        return existing;
    }

    public boolean deleteByKey(String pluginId, String collection, String key) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        int deleted = em.createQuery("delete from PluginData pd where pd.pluginId = :pluginId and pd.collection = :collection and pd.key = :key")
                .setParameter("pluginId", pluginId)
                .setParameter("collection", collection)
                .setParameter("key", key)
                .executeUpdate();
        return deleted > 0;
    }

    public void deleteByPluginId(String pluginId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        em.createQuery("delete from PluginData pd where pd.pluginId = :pluginId")
                .setParameter("pluginId", pluginId)
                .executeUpdate();
    }
}
