package com.sismics.docs.core.util.plugin;

import java.util.List;
import java.util.Map;

/**
 * Execution context passed to a {@link PluginHandler} invocation.
 */
public class PluginContext {
    private final String pluginId;
    private final String userId;
    private final String username;
    private final String userDisplayName;
    private final boolean admin;
    private final List<String> targetIdList;
    private final Map<String, String> queryParams;
    private final String requestBody;

    public PluginContext(String pluginId, String userId, String username, String userDisplayName,
                         boolean admin, List<String> targetIdList,
                         Map<String, String> queryParams, String requestBody) {
        this.pluginId = pluginId;
        this.userId = userId;
        this.username = username;
        this.userDisplayName = userDisplayName;
        this.admin = admin;
        this.targetIdList = targetIdList;
        this.queryParams = queryParams;
        this.requestBody = requestBody;
    }

    public String getPluginId() { return pluginId; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getUserDisplayName() { return userDisplayName; }
    public boolean isAdmin() { return admin; }
    public List<String> getTargetIdList() { return targetIdList; }
    public Map<String, String> getQueryParams() { return queryParams; }
    public String getRequestBody() { return requestBody; }
}
