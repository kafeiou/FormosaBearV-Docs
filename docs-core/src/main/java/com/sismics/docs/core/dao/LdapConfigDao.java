package com.sismics.docs.core.dao;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Strings;
import com.sismics.docs.core.model.jpa.LdapConfig;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

/**
 * LDAP configuration DAO.
 */
public class LdapConfigDao {
    /**
     * Returns all active (non-deleted) LDAP configurations ordered by priority order ascending.
     *
     * @return List of LDAP configurations
     */
    public List<LdapConfig> findAll() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<LdapConfig> q = em.createQuery(
                "select l from LdapConfig l where l.deleteDate is null order by l.order asc, l.id asc",
                LdapConfig.class);
        return q.getResultList();
    }

    /**
     * Returns all enabled and active LDAP configurations ordered by priority order ascending.
     *
     * @return List of enabled LDAP configurations
     */
    public List<LdapConfig> findEnabled() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<LdapConfig> q = em.createQuery(
                "select l from LdapConfig l where l.deleteDate is null and l.enabled = true order by l.order asc, l.id asc",
                LdapConfig.class);
        return q.getResultList();
    }

    /**
     * Gets an active LDAP configuration by ID.
     *
     * @param id LDAP configuration ID
     * @return LdapConfig or null
     */
    public LdapConfig getActiveById(String id) {
        if (Strings.isNullOrEmpty(id)) {
            return null;
        }
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        TypedQuery<LdapConfig> q = em.createQuery(
                "select l from LdapConfig l where l.id = :id and l.deleteDate is null",
                LdapConfig.class);
        q.setParameter("id", id);
        try {
            return q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Creates a new LDAP configuration.
     *
     * @param ldapConfig LDAP configuration to create
     * @return ID
     */
    public String create(LdapConfig ldapConfig) {
        if (Strings.isNullOrEmpty(ldapConfig.getId())) {
            ldapConfig.setId(UUID.randomUUID().toString());
        }
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        em.persist(ldapConfig);
        return ldapConfig.getId();
    }

    /**
     * Updates an existing LDAP configuration.
     *
     * @param ldapConfig LDAP configuration to update
     * @return Updated LdapConfig
     */
    public LdapConfig update(LdapConfig ldapConfig) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        LdapConfig dbConfig = getActiveById(ldapConfig.getId());
        if (dbConfig == null) {
            return null;
        }
        dbConfig.setName(ldapConfig.getName());
        dbConfig.setOrder(ldapConfig.getOrder());
        dbConfig.setEnabled(ldapConfig.isEnabled());
        dbConfig.setHost(ldapConfig.getHost());
        dbConfig.setPort(ldapConfig.getPort());
        dbConfig.setUseSsl(ldapConfig.isUseSsl());
        dbConfig.setUseStartTls(ldapConfig.isUseStartTls());
        dbConfig.setAdminDn(ldapConfig.getAdminDn());
        dbConfig.setAdminPassword(ldapConfig.getAdminPassword());
        dbConfig.setBaseDn(ldapConfig.getBaseDn());
        dbConfig.setFilter(ldapConfig.getFilter());
        dbConfig.setDefaultEmail(ldapConfig.getDefaultEmail());
        dbConfig.setDefaultStorage(ldapConfig.getDefaultStorage());
        dbConfig.setDefaultGroup(ldapConfig.getDefaultGroup());
        dbConfig.setDefaultGroupMembership(ldapConfig.getDefaultGroupMembership());
        return dbConfig;
    }

    /**
     * Soft-deletes an LDAP configuration by ID.
     *
     * @param id LDAP configuration ID
     */
    public void delete(String id) {
        LdapConfig dbConfig = getActiveById(id);
        if (dbConfig != null) {
            dbConfig.setDeleteDate(new Date());
        }
    }
}
