package com.sismics.docs.core.util;

import com.sismics.docs.core.constant.AuditLogType;
import com.sismics.docs.core.dao.AuditLogDao;
import com.sismics.docs.core.model.jpa.AuditLog;
import com.sismics.docs.core.model.jpa.Loggable;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.persistence.EntityManager;

/**
 * Audit log utilities.
 * 
 * @author bgamard
 */
public class AuditLogUtil {
    /**
     * Create an audit log.
     * 
     * @param loggable Loggable
     * @param type Audit log type
     * @param userId User ID
     */
    public static void create(Loggable loggable, AuditLogType type, String userId) {
        create(loggable, type, userId, loggable.toMessage());
    }

    /**
     * Create an audit log with a custom message.
     *
     * @param loggable Loggable entity
     * @param type Audit log type
     * @param userId User ID performing the action
     * @param message Custom audit log message
     */
    public static void create(Loggable loggable, AuditLogType type, String userId, String message) {
        if (userId == null) {
            userId = "admin";
        }

        // Get the entity ID
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        String entityId = (String) em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(loggable);
        
        // Create the audit log
        AuditLogDao auditLogDao = new AuditLogDao();
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setEntityId(entityId);
        auditLog.setEntityClass(loggable.getClass().getSimpleName());
        auditLog.setType(type);
        auditLog.setMessage(message);
        auditLogDao.create(auditLog);
    }
}
