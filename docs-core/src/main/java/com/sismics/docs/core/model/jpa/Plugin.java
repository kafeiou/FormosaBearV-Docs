package com.sismics.docs.core.model.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Date;

/**
 * Installed plugin entity.
 */
@Entity
@Table(name = "T_PLUGIN")
public class Plugin {
    @Id
    @Column(name = "PLG_ID_C", length = 64)
    private String id;

    @Column(name = "PLG_NAME_C", length = 100, nullable = false)
    private String name;

    @Column(name = "PLG_VERSION_C", length = 36, nullable = false)
    private String version;

    @Column(name = "PLG_DESCRIPTION_C", length = 500)
    private String description;

    @Column(name = "PLG_ENTRY_C", length = 255, nullable = false)
    private String entry;

    @Column(name = "PLG_NAVLABEL_C", length = 100)
    private String navLabel;

    @Column(name = "PLG_NAVICON_C", length = 64)
    private String navIcon;

    @Column(name = "PLG_HANDLER_C", length = 64)
    private String handler;

    @Column(name = "PLG_MANIFEST_C", columnDefinition = "text")
    private String manifest;

    @Column(name = "PLG_ENABLED_B", nullable = false)
    private boolean enabled;

    @Column(name = "PLG_CREATEDATE_D", nullable = false)
    private Date createDate;

    @Column(name = "PLG_UPDATEDATE_D", nullable = false)
    private Date updateDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEntry() { return entry; }
    public void setEntry(String entry) { this.entry = entry; }

    public String getNavLabel() { return navLabel; }
    public void setNavLabel(String navLabel) { this.navLabel = navLabel; }

    public String getNavIcon() { return navIcon; }
    public void setNavIcon(String navIcon) { this.navIcon = navIcon; }

    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }

    public String getManifest() { return manifest; }
    public void setManifest(String manifest) { this.manifest = manifest; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Date getCreateDate() { return createDate; }
    public void setCreateDate(Date createDate) { this.createDate = createDate; }

    public Date getUpdateDate() { return updateDate; }
    public void setUpdateDate(Date updateDate) { this.updateDate = updateDate; }
}
