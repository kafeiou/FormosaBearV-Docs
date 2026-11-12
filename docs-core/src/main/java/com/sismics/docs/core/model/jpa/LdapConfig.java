package com.sismics.docs.core.model.jpa;

import java.util.Date;

import com.google.common.base.MoreObjects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * LDAP configuration entity supporting multiple LDAP directory servers.
 */
@Entity
@Table(name = "T_LDAP_CONFIG")
public class LdapConfig {
    @Id
    @Column(name = "LDC_ID_C", nullable = false, length = 36)
    private String id;

    @Column(name = "LDC_NAME_C", nullable = false, length = 100)
    private String name;

    @Column(name = "LDC_ORDER_N", nullable = false)
    private Integer order;

    @Column(name = "LDC_ENABLED_B", nullable = false)
    private boolean enabled;

    @Column(name = "LDC_HOST_C", nullable = false, length = 250)
    private String host;

    @Column(name = "LDC_PORT_N", nullable = false)
    private Integer port;

    @Column(name = "LDC_USESSL_B", nullable = false)
    private boolean useSsl;

    @Column(name = "LDC_ADMIN_DN_C", nullable = false, length = 250)
    private String adminDn;

    @Column(name = "LDC_ADMIN_PASSWORD_C", nullable = false, length = 250)
    private String adminPassword;

    @Column(name = "LDC_BASE_DN_C", nullable = false, length = 250)
    private String baseDn;

    @Column(name = "LDC_FILTER_C", nullable = false, length = 250)
    private String filter;

    @Column(name = "LDC_DEFAULT_EMAIL_C", nullable = false, length = 250)
    private String defaultEmail;

    @Column(name = "LDC_DEFAULT_STORAGE_N", nullable = false)
    private Long defaultStorage;

    @Column(name = "LDC_DEFAULT_GROUP_C", length = 36)
    private String defaultGroup;

    @Column(name = "LDC_DEFAULT_GROUPMEMBERSHIP_C", length = 250)
    private String defaultGroupMembership;

    @Column(name = "LDC_DELETEDATE_D")
    private Date deleteDate;

    public String getId() {
        return id;
    }

    public LdapConfig setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public LdapConfig setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getOrder() {
        return order;
    }

    public LdapConfig setOrder(Integer order) {
        this.order = order;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LdapConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String getHost() {
        return host;
    }

    public LdapConfig setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public LdapConfig setPort(Integer port) {
        this.port = port;
        return this;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public LdapConfig setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
        return this;
    }

    public String getAdminDn() {
        return adminDn;
    }

    public LdapConfig setAdminDn(String adminDn) {
        this.adminDn = adminDn;
        return this;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public LdapConfig setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
        return this;
    }

    public String getBaseDn() {
        return baseDn;
    }

    public LdapConfig setBaseDn(String baseDn) {
        this.baseDn = baseDn;
        return this;
    }

    public String getFilter() {
        return filter;
    }

    public LdapConfig setFilter(String filter) {
        this.filter = filter;
        return this;
    }

    public String getDefaultEmail() {
        return defaultEmail;
    }

    public LdapConfig setDefaultEmail(String defaultEmail) {
        this.defaultEmail = defaultEmail;
        return this;
    }

    public Long getDefaultStorage() {
        return defaultStorage;
    }

    public LdapConfig setDefaultStorage(Long defaultStorage) {
        this.defaultStorage = defaultStorage;
        return this;
    }

    public String getDefaultGroup() {
        return defaultGroup;
    }

    public LdapConfig setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
        return this;
    }

    public String getDefaultGroupMembership() {
        return defaultGroupMembership;
    }

    public LdapConfig setDefaultGroupMembership(String defaultGroupMembership) {
        this.defaultGroupMembership = defaultGroupMembership;
        return this;
    }

    public Date getDeleteDate() {
        return deleteDate;
    }

    public LdapConfig setDeleteDate(Date deleteDate) {
        this.deleteDate = deleteDate;
        return this;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("name", name)
                .add("order", order)
                .add("enabled", enabled)
                .add("host", host)
                .add("port", port)
                .toString();
    }
}
