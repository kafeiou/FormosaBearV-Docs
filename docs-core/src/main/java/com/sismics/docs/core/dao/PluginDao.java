package com.sismics.docs.core.dao;

import com.sismics.docs.core.model.jpa.Plugin;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.Date;
import java.util.List;

/**
 * Plugin DAO.
 */
public class PluginDao {
    public Plugin getById(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        return em.find(Plugin.class, id);
    }

    public List<Plugin> findAll() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<Plugin> q = em.createQuery("select p from Plugin p order by p.createDate asc", Plugin.class);
        return q.getResultList();
    }

    public List<Plugin> findEnabled() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<Plugin> q = em.createQuery("select p from Plugin p where p.enabled = true order by p.createDate asc", Plugin.class);
        return q.getResultList();
    }

    public Plugin createOrUpdate(Plugin plugin) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Date now = new Date();
        Plugin existing = em.find(Plugin.class, plugin.getId());
        if (existing == null) {
            plugin.setCreateDate(now);
            plugin.setUpdateDate(now);
            em.persist(plugin);
            return plugin;
        }
        existing.setName(plugin.getName());
        existing.setVersion(plugin.getVersion());
        existing.setDescription(plugin.getDescription());
        existing.setEntry(plugin.getEntry());
        existing.setNavLabel(plugin.getNavLabel());
        existing.setNavIcon(plugin.getNavIcon());
        existing.setHandler(plugin.getHandler());
        existing.setManifest(plugin.getManifest());
        existing.setUpdateDate(now);
        return existing;
    }

    public void setEnabled(String id, boolean enabled) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Plugin plugin = em.find(Plugin.class, id);
        if (plugin != null) {
            plugin.setEnabled(enabled);
            plugin.setUpdateDate(new Date());
        }
    }

    public void delete(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Plugin plugin = em.find(Plugin.class, id);
        if (plugin != null) {
            em.remove(plugin);
        }
    }
}
