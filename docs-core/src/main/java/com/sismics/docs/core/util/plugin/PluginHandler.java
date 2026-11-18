package com.sismics.docs.core.util.plugin;

import java.util.Optional;

/**
 * SPI interface for backend plugin handlers.
 */
public interface PluginHandler {
    /**
     * Handler identifier referenced by plugin.json "handler".
     */
    String getId();

    /**
     * Handles an action invoked via /api/plugin/{pluginId}/action/{action}.
     */
    PluginResult handleAction(String method, String action, PluginContext context) throws Exception;

    /**
     * Checks whether the given tagId is actively referenced by this plugin's data.
     * Returns a human-readable reason if deletion of the tag should be blocked.
     */
    default Optional<String> findTagReferenceReason(String tagId) {
        return Optional.empty();
    }
}
