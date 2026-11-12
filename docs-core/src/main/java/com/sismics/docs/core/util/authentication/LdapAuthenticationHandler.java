package com.sismics.docs.core.util.authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.filter.FilterEncoder;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.ConfigDao;
import com.sismics.docs.core.dao.GroupDao;
import com.sismics.docs.core.dao.LdapConfigDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.model.jpa.Config;
import com.sismics.docs.core.model.jpa.LdapConfig;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.model.jpa.UserGroup;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.util.ClasspathScanner;

/**
 * LDAP authentication handler supporting multiple LDAP servers in priority order.
 *
 * @author bgamard
 */
@ClasspathScanner.Priority(50) // Before the internal database
public class LdapAuthenticationHandler implements AuthenticationHandler {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(LdapAuthenticationHandler.class);

    /**
     * Build an LdapConnection for a given LdapConfig.
     */
    private static LdapConnection createConnection(LdapConfig ldapCfg) {
        LdapConnectionConfig config = new LdapConnectionConfig();
        config.setLdapHost(ldapCfg.getHost());
        config.setLdapPort(ldapCfg.getPort() != null ? ldapCfg.getPort() : 389);
        config.setUseSsl(ldapCfg.isUseSsl());
        config.setName(ldapCfg.getAdminDn());
        config.setCredentials(ldapCfg.getAdminPassword());
        config.setTimeout(5000L);
        return new LdapNetworkConnection(config);
    }

    /**
     * Retrieve enabled LDAP configurations in priority order, falling back to legacy T_CONFIG if T_LDAP_CONFIG is empty.
     */
    private List<LdapConfig> getEnabledConfigs() {
        LdapConfigDao ldapConfigDao = new LdapConfigDao();
        List<LdapConfig> allConfigs = ldapConfigDao.findAll();
        if (!allConfigs.isEmpty()) {
            return ldapConfigDao.findEnabled();
        }

        // Backward-compatibility fallback to T_CONFIG if no rows exist in T_LDAP_CONFIG
        ConfigDao configDao = new ConfigDao();
        Config ldapEnabled = configDao.getById(ConfigType.LDAP_ENABLED);
        if (ldapEnabled == null || !Boolean.parseBoolean(ldapEnabled.getValue())) {
            return new ArrayList<>();
        }
        try {
            LdapConfig legacy = new LdapConfig()
                    .setId("legacy-default")
                    .setName("Primary LDAP")
                    .setOrder(0)
                    .setEnabled(true)
                    .setHost(ConfigUtil.getConfigStringValue(ConfigType.LDAP_HOST))
                    .setPort(ConfigUtil.getConfigIntegerValue(ConfigType.LDAP_PORT))
                    .setUseSsl(ConfigUtil.getConfigBooleanValue(ConfigType.LDAP_USESSL, false))
                    .setAdminDn(ConfigUtil.getConfigStringValue(ConfigType.LDAP_ADMIN_DN))
                    .setAdminPassword(ConfigUtil.getConfigStringValue(ConfigType.LDAP_ADMIN_PASSWORD))
                    .setBaseDn(ConfigUtil.getConfigStringValue(ConfigType.LDAP_BASE_DN))
                    .setFilter(ConfigUtil.getConfigStringValue(ConfigType.LDAP_FILTER))
                    .setDefaultEmail(ConfigUtil.getConfigStringValue(ConfigType.LDAP_DEFAULT_EMAIL))
                    .setDefaultStorage(ConfigUtil.getConfigLongValue(ConfigType.LDAP_DEFAULT_STORAGE));
            Config groupCfg = configDao.getById(ConfigType.LDAP_DEFAULT_GROUP);
            if (groupCfg != null) {
                legacy.setDefaultGroup(groupCfg.getValue());
            }
            Config groupAttrCfg = configDao.getById(ConfigType.LDAP_DEFAULT_GROUPMEMBERSHIP);
            if (groupAttrCfg != null) {
                legacy.setDefaultGroupMembership(groupAttrCfg.getValue());
            }
            List<LdapConfig> list = new ArrayList<>();
            list.add(legacy);
            return list;
        } catch (Exception e) {
            log.warn("Legacy LDAP configuration in T_CONFIG is incomplete", e);
            return new ArrayList<>();
        }
    }

    @Override
    public User authenticate(String username, String password) {
        List<LdapConfig> enabledConfigs = getEnabledConfigs();
        if (enabledConfigs.isEmpty()) {
            return null;
        }

        // Escape username per RFC 4515 before substitution to prevent LDAP injection (CWE-90).
        // FilterEncoder.encodeFilterValue() escapes: \ * ( ) \x00 and other metacharacters.
        String safeUsername = FilterEncoder.encodeFilterValue(username);

        Entry matchedEntry = null;
        LdapConfig matchedConfig = null;
        String sGroupName = null;

        for (LdapConfig ldapCfg : enabledConfigs) {
            try (LdapConnection ldapConnection = createConnection(ldapCfg)) {
                ldapConnection.bind();

                try (EntryCursor cursor = ldapConnection.search(
                        ldapCfg.getBaseDn(),
                        ldapCfg.getFilter().replace("USERNAME", safeUsername),
                        SearchScope.SUBTREE)) {
                    if (cursor.next()) {
                        Entry candidateEntry = cursor.get();
                        ldapConnection.bind(candidateEntry.getDn(), password);

                        matchedEntry = candidateEntry;
                        matchedConfig = ldapCfg;
                        String groupAttr = ldapCfg.getDefaultGroupMembership();
                        if (!Strings.isNullOrEmpty(groupAttr) && candidateEntry.get(groupAttr) != null) {
                            sGroupName = candidateEntry.get(groupAttr).getString();
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Error authenticating \"{}\" on LDAP server \"{}\" ({}:{}), trying next server if available",
                        username, ldapCfg.getName(), ldapCfg.getHost(), ldapCfg.getPort(), e);
            }
        }

        if (matchedEntry == null || matchedConfig == null) {
            return null;
        }

        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(username);
        if (user == null) {
            // The user is valid but never authenticated, create the user now
            log.info("\"{}\" authenticated for the first time via LDAP \"{}\", creating the internal user",
                    username, matchedConfig.getName());
            user = new User();
            user.setRoleId(Constants.DEFAULT_USER_ROLE);
            user.setUsername(username);
            user.setAuthSource("LDAP");
            user.setPassword(UUID.randomUUID().toString()); // No authentication using the internal database
            Attribute mailAttribute = matchedEntry.get("mail");
            if (mailAttribute == null || mailAttribute.get() == null) {
                user.setEmail(matchedConfig.getDefaultEmail());
            } else {
                Value value = mailAttribute.get();
                user.setEmail(value.getString());
            }
            user.setStorageQuota(matchedConfig.getDefaultStorage());
            try {
                userDao.create(user, "admin");
                String defaultGroupId = matchedConfig.getDefaultGroup();
                if (!Strings.isNullOrEmpty(defaultGroupId) && !defaultGroupId.trim().isEmpty()) {
                    GroupDao groupDao = new GroupDao();
                    UserGroup userGroup = new UserGroup();
                    userGroup.setGroupId(defaultGroupId.trim());
                    userGroup.setUserId(user.getId());
                    if (groupDao.getActiveById(defaultGroupId.trim()) != null) {
                        groupDao.addMember(userGroup);
                    }
                }
                if (sGroupName != null) {
                    GroupDao groupDao = new GroupDao();
                    UserGroup userGroup = new UserGroup();
                    userGroup.setUserId(user.getId());
                    com.sismics.docs.core.model.jpa.Group aGroup = groupDao.getActiveByName(sGroupName);
                    if (aGroup != null) {
                        userGroup.setGroupId(aGroup.getId());
                        groupDao.addMember(userGroup);
                    }
                }
            } catch (Exception e) {
                log.error("Error while creating the internal user", e);
                return null;
            }
        } else {
            // Reject login if the existing user account has been disabled by an administrator
            if (user.getDisableDate() != null) {
                log.warn("Rejected LDAP login for disabled user \"" + username + "\"");
                return null;
            }
            // Existing user authenticated via LDAP
            // Never convert the built-in admin account to LDAP to preserve break-glass emergency access
            if (!Constants.ADMIN_USER_ID.equals(user.getId()) && !"admin".equalsIgnoreCase(username)) {
                if (!"LDAP".equals(user.getAuthSource())) {
                    log.info("Migrating user \"" + username + "\" auth_source to LDAP and resetting local password");
                    user.setAuthSource("LDAP");
                    user.setPassword(UUID.randomUUID().toString());
                    userDao.updatePassword(user, "admin");
                    userDao.update(user, "admin");
                }
            }
        }

        return user;
    }
}
