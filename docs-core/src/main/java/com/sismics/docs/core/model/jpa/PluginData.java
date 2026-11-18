package com.sismics.docs.core.model.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Date;

/**
 * Generic JSON document storage for plugins.
 */
@Entity
@Table(name = "T_PLUGIN_DATA")
public class PluginData {
    @Id
    @Column(name = "PLD_ID_C", length = 36)
    private String id;

    @Column(name = "PLD_IDPLUGIN_C", length = 64, nullable = false)
    private String pluginId;

    @Column(name = "PLD_COLLECTION_C", length = 64, nullable = false)
    private String collection;

    @Column(name = "PLD_KEY_C", length = 128, nullable = false)
    private String key;

    @Column(name = "PLD_IDUSER_C", length = 36)
    private String userId;

    @Column(name = "PLD_VALUE_C", columnDefinition = "text", nullable = false)
    private String value;

    @Column(name = "PLD_CREATEDATE_D", nullable = false)
    private Date createDate;

    @Column(name = "PLD_UPDATEDATE_D", nullable = false)
    private Date updateDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Date getCreateDate() { return createDate; }
    public void setCreateDate(Date createDate) { this.createDate = createDate; }

    public Date getUpdateDate() { return updateDate; }
    public void setUpdateDate(Date updateDate) { this.updateDate = updateDate; }
}
