package com.sismics.docs.core.model.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Plugin-protected tag binding entity.
 */
@Entity
@Table(name = "T_PLUGIN_TAG")
public class PluginTag {
    @Id
    @Column(name = "PTG_ID_C", length = 36)
    private String id;

    @Column(name = "PTG_IDPLUGIN_C", length = 64, nullable = false)
    private String pluginId;

    @Column(name = "PTG_IDTAG_C", length = 36, nullable = false)
    private String tagId;

    @Column(name = "PTG_ROLE_C", length = 32, nullable = false)
    private String role;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }

    public String getTagId() { return tagId; }
    public void setTagId(String tagId) { this.tagId = tagId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
