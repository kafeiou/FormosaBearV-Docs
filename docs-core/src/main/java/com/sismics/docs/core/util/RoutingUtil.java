package com.sismics.docs.core.util;

import com.google.common.collect.Lists;
import com.sismics.docs.core.constant.AclTargetType;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.RouteModelDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.criteria.UserCriteria;
import com.sismics.docs.core.dao.dto.RouteStepDto;
import com.sismics.docs.core.dao.dto.UserDto;
import com.sismics.docs.core.event.DocumentUpdatedAsyncEvent;
import com.sismics.docs.core.event.RouteStepValidateEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.Acl;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.RouteModel;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.List;

/**
 * Routing utilities.
 *
 * @author bgamard
 */
public class RoutingUtil {
    /**
     * Update routing ACLs according to the current route step.
     *
     * @param documentId Document ID
     * @param currentStep Current route step
     * @param previousStep Previous route step
     * @param userId User ID
     */
    public static void updateAcl(String documentId, RouteStepDto currentStep, RouteStepDto previousStep, String userId) {
        AclDao aclDao = new AclDao();

        if (previousStep != null) {
            // Remove the previous ACL
            aclDao.delete(documentId, PermType.READ, previousStep.getTargetId(), userId, AclType.ROUTING);
        }

        if (currentStep != null) {
            // Create a temporary READ ACL
            Acl acl = new Acl();
            acl.setPerm(PermType.READ);
            acl.setType(AclType.ROUTING);
            acl.setSourceId(documentId);
            acl.setTargetId(currentStep.getTargetId());
            aclDao.create(acl, userId);
        }

        // Raise a document updated event
        DocumentUpdatedAsyncEvent event = new DocumentUpdatedAsyncEvent();
        event.setUserId(userId);
        event.setDocumentId(documentId);
        ThreadLocalContext.get().addAsyncEvent(event);
    }

    /**
     * Send an email when a route step is validated.
     *
     * @param documentId Document ID
     * @param routeStepDto Route step DTO
     */
    public static void sendRouteStepEmail(String documentId, RouteStepDto routeStepDto) {
        DocumentDao documentDao = new DocumentDao();
        Document document = documentDao.getById(documentId);

        List<UserDto> userDtoList = Lists.newArrayList();
                UserDao userDao = new UserDao();
        switch (AclTargetType.valueOf(routeStepDto.getTargetType())) {
            case USER:
                userDtoList.addAll(userDao.findByCriteria(new UserCriteria().setUserId(routeStepDto.getTargetId()), null));
                break;
            case GROUP:
                userDtoList.addAll(userDao.findByCriteria(new UserCriteria().setGroupId(routeStepDto.getTargetId()), null));
                break;
        }

        // Fire route step validate events
        for (UserDto userDto : userDtoList) {
            RouteStepValidateEvent routeStepValidateEvent = new RouteStepValidateEvent();
            routeStepValidateEvent.setUser(userDto);
            routeStepValidateEvent.setDocument(document);
            AppContext.getInstance().getMailEventBus().post(routeStepValidateEvent);
        }
    }

    /**
     * Find the first route model name matching a target type and name.
     *
     * @param targetType Target type
     * @param targetName Target name
     * @return Route model name or null if none is matching
     */
    public static String findRouteModelNameByTargetName(AclTargetType targetType, String targetName) {
        RouteModelDao routeModelDao = new RouteModelDao();
        List<RouteModel> routeModelList = routeModelDao.findAll();
        for (RouteModel routeModel : routeModelList) {
            try (JsonReader reader = Json.createReader(new StringReader(routeModel.getSteps()))) {
                JsonArray stepsJson = reader.readArray();
                for (int order = 0; order < stepsJson.size(); order++) {
                    JsonObject step = stepsJson.getJsonObject(order);
                    JsonObject target = step.getJsonObject("target");
                    AclTargetType routeTargetType = AclTargetType.valueOf(target.getString("type"));
                    String routeTargetName = target.getString("name");
                    if (targetType == routeTargetType && targetName.equals(routeTargetName)) {
                        return routeModel.getName();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Transfers workflow route models and active route steps from sourceUser to targetUser.
     *
     * @param sourceUser Source user
     * @param targetUser Target user
     * @param actorUserId Admin user ID performing the transfer
     * @return Number of updated route models and active route steps
     */
    public static int transferUserInRoutes(com.sismics.docs.core.model.jpa.User sourceUser, com.sismics.docs.core.model.jpa.User targetUser, String actorUserId) {
        int updatedCount = 0;
        RouteModelDao routeModelDao = new RouteModelDao();
        AclDao aclDao = new AclDao();
        List<RouteModel> routeModelList = routeModelDao.findAll();
        for (RouteModel routeModel : routeModelList) {
            boolean modified = false;
            jakarta.json.JsonArrayBuilder newStepsBuilder = Json.createArrayBuilder();
            try (JsonReader reader = Json.createReader(new StringReader(routeModel.getSteps()))) {
                JsonArray stepsJson = reader.readArray();
                for (int order = 0; order < stepsJson.size(); order++) {
                    JsonObject step = stepsJson.getJsonObject(order);
                    JsonObject target = step.getJsonObject("target");
                    AclTargetType routeTargetType = AclTargetType.valueOf(target.getString("type"));
                    String routeTargetName = target.getString("name");
                    if (routeTargetType == AclTargetType.USER && sourceUser.getUsername().equals(routeTargetName)) {
                        jakarta.json.JsonObjectBuilder stepBuilder = Json.createObjectBuilder();
                        for (String key : step.keySet()) {
                            if ("target".equals(key)) {
                                stepBuilder.add("target", Json.createObjectBuilder()
                                        .add("type", AclTargetType.USER.name())
                                        .add("name", targetUser.getUsername()));
                            } else {
                                stepBuilder.add(key, step.get(key));
                            }
                        }
                        newStepsBuilder.add(stepBuilder);
                        modified = true;
                    } else {
                        newStepsBuilder.add(step);
                    }
                }
            }
            if (modified) {
                routeModel.setSteps(newStepsBuilder.build().toString());
                routeModelDao.update(routeModel, actorUserId);
                updatedCount++;
            }

            // If sourceUser had ACL on this RouteModel, ensure targetUser also gets READ/WRITE ACL
            if (aclDao.checkPermission(routeModel.getId(), PermType.WRITE, Lists.newArrayList(sourceUser.getId()))) {
                aclDao.ensureDirectUserReadWriteAcl(routeModel.getId(), targetUser.getId(), actorUserId);
            }
        }

        // Update active route steps where RTP_IDTARGET_C == sourceUser.getId() and RTP_ENDDATE_D is null
        jakarta.persistence.EntityManager em = ThreadLocalContext.get().getEntityManager();
        jakarta.persistence.Query stepQuery = em.createNativeQuery(
                "update T_ROUTE_STEP set RTP_IDTARGET_C = :targetId where RTP_IDTARGET_C = :sourceId and RTP_ENDDATE_D is null and RTP_DELETEDATE_D is null");
        stepQuery.setParameter("targetId", targetUser.getId());
        stepQuery.setParameter("sourceId", sourceUser.getId());
        updatedCount += stepQuery.executeUpdate();

        // Update temporary ROUTING ACLs from sourceUser to targetUser
        jakarta.persistence.Query routingAclQuery = em.createNativeQuery(
                "update T_ACL set ACL_TARGETID_C = :targetId where ACL_TARGETID_C = :sourceId and ACL_TYPE_C = 'ROUTING' and ACL_DELETEDATE_D is null");
        routingAclQuery.setParameter("targetId", targetUser.getId());
        routingAclQuery.setParameter("sourceId", sourceUser.getId());
        routingAclQuery.executeUpdate();

        return updatedCount;
    }
}
