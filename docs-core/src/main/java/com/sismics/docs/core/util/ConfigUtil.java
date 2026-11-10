package com.sismics.docs.core.util;

import java.io.StringReader;
import java.util.ResourceBundle;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.dao.ConfigDao;
import com.sismics.docs.core.model.jpa.Config;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Configuration parameter utilities.
 *
 */
public class ConfigUtil {

    private static Boolean cachedFileEncrypt = null;

    public static void resetCache() {
        cachedFileEncrypt = null;
    }

    private static boolean hasActiveEntityManager() {
        return ThreadLocalContext.get() != null && ThreadLocalContext.get().getEntityManager() != null;
    }

    private static boolean getBundleBoolean(String key, boolean defaultVal) {
        try {
            return "1".equals(getConfigBundle().getString(key).trim())
                    || "true".equalsIgnoreCase(getConfigBundle().getString(key).trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * Returns true if uploaded files should be encrypted on disk.
     * Once locked in the database, this value can never be changed.
     */
    public static boolean isFileEncrypt() {
        if (hasActiveEntityManager()) {
            try {
                ConfigDao configDao = new ConfigDao();
                Config config = configDao.getById(ConfigType.FILE_ENCRYPT);
                if (config != null && config.getValue() != null) {
                    boolean val = "1".equals(config.getValue().trim()) || Boolean.parseBoolean(config.getValue().trim());
                    cachedFileEncrypt = val;
                    return val;
                }
            } catch (Exception ignored) {
            }
        }
        if (cachedFileEncrypt == null) {
            cachedFileEncrypt = getBundleBoolean("file.encrypt", false);
        }
        return cachedFileEncrypt;
    }

    /**
     * Returns true if the file encryption mode has been permanently locked.
     * Also automatically locks if any file already exists in T_FILE.
     */
    public static boolean isFileEncryptLocked() {
        if (!hasActiveEntityManager()) {
            return true;
        }
        try {
            ConfigDao configDao = new ConfigDao();
            Config lockedConfig = configDao.getById(ConfigType.FILE_ENCRYPT_LOCKED);
            if (lockedConfig != null && ("1".equals(lockedConfig.getValue()) || Boolean.parseBoolean(lockedConfig.getValue()))) {
                return true;
            }
            EntityManager em = ThreadLocalContext.get().getEntityManager();
            Query q = em.createNativeQuery("select count(f.FIL_ID_C) from T_FILE f");
            long fileCount = ((Number) q.getSingleResult()).longValue();
            if (fileCount > 0) {
                boolean currentEncrypt = isFileEncrypt();
                configDao.update(ConfigType.FILE_ENCRYPT, Boolean.toString(currentEncrypt));
                configDao.update(ConfigType.FILE_ENCRYPT_LOCKED, "true");
                cachedFileEncrypt = currentEncrypt;
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Permanently lock the file encryption mode.
     */
    public static void lockFileEncrypt(boolean encrypt) {
        cachedFileEncrypt = encrypt;
        if (hasActiveEntityManager()) {
            ConfigDao configDao = new ConfigDao();
            configDao.update(ConfigType.FILE_ENCRYPT, Boolean.toString(encrypt));
            configDao.update(ConfigType.FILE_ENCRYPT_LOCKED, "true");
        }
    }

    /**
     * Ensure file encryption mode is locked (called when the first file is created).
     */
    public static void ensureFileEncryptLocked() {
        if (hasActiveEntityManager() && !isFileEncryptLocked()) {
            lockFileEncrypt(isFileEncrypt());
        }
    }

    /**
     * Returns true if deleting a file should permanently delete it from disk (instead of moving to delete/storage).
     */
    public static boolean isFileDelete() {
        if (hasActiveEntityManager()) {
            try {
                ConfigDao configDao = new ConfigDao();
                Config config = configDao.getById(ConfigType.FILE_DELETE);
                if (config != null && config.getValue() != null) {
                    return "1".equals(config.getValue().trim()) || Boolean.parseBoolean(config.getValue().trim());
                }
            } catch (Exception ignored) {
            }
        }
        return getBundleBoolean("file.delete", false);
    }

    /**
     * Returns true if duplicate filenames are allowed for the same user.
     */
    public static boolean canFileDuplicate() {
        if (hasActiveEntityManager()) {
            try {
                ConfigDao configDao = new ConfigDao();
                Config config = configDao.getById(ConfigType.FILE_DUPLICATE);
                if (config != null && config.getValue() != null) {
                    return "1".equals(config.getValue().trim()) || Boolean.parseBoolean(config.getValue().trim());
                }
            } catch (Exception ignored) {
            }
        }
        return getBundleBoolean("file.duplicate", false);
    }

    /**
     * Returns true if file download is enabled. Default is true.
     */
    public static boolean canFileDownload() {
        if (hasActiveEntityManager()) {
            try {
                ConfigDao configDao = new ConfigDao();
                Config config = configDao.getById(ConfigType.FILE_DOWNLOAD);
                if (config != null && config.getValue() != null) {
                    return "1".equals(config.getValue().trim()) || Boolean.parseBoolean(config.getValue().trim());
                }
            } catch (Exception ignored) {
            }
        }
        return getBundleBoolean("file.download", true);
    }

    /**
     * Returns the textual value of a configuration parameter.
     *
     * @param configType Type of the configuration parameter
     * @return Textual value of the configuration parameter
     * @throws IllegalStateException Configuration parameter undefined
     */
    public static String getConfigStringValue(ConfigType configType) {
        ConfigDao configDao = new ConfigDao();
        Config config = configDao.getById(configType);
        if (config == null) {
            throw new IllegalStateException("Config parameter not found: " + configType);
        }
        return config.getValue();
    }

    /**
     * Returns the configuration resource bundle.
     *
     * @return Resource bundle
     */
    public static ResourceBundle getConfigBundle() {
        return ResourceBundle.getBundle("config");
    }

    /**
     * Returns the integer value of a configuration parameter.
     *
     * @param configType Type of the configuration parameter
     * @return Integer value of the configuration parameter
     * @throws IllegalStateException Configuration parameter undefined
     */
    public static int getConfigIntegerValue(ConfigType configType) {
        String value = getConfigStringValue(configType);

        return Integer.parseInt(value);
    }

    /**
     * Returns the long value of a configuration parameter.
     *
     * @param configType Type of the configuration parameter
     * @return Long value of the configuration parameter
     * @throws IllegalStateException Configuration parameter undefined
     */
    public static long getConfigLongValue(ConfigType configType) {
        String value = getConfigStringValue(configType);

        return Long.parseLong(value);
    }

    /**
     * Returns the boolean value of a configuration parameter.
     *
     * @param configType Type of the configuration parameter
     * @return Boolean value of the configuration parameter
     * @throws IllegalStateException Configuration parameter undefined
     */
    public static boolean getConfigBooleanValue(ConfigType configType) {
        String value = getConfigStringValue(configType);
        return Boolean.parseBoolean(value);
    }

    /**
     * Returns the boolean value of a configuration parameter with a default value.
     *
     * @param configType Type of the configuration parameter
     * @param defaultValue Default value to return if the configuration parameter is undefined
     * @return Boolean value of the configuration parameter
     */
    public static boolean getConfigBooleanValue(ConfigType configType, boolean defaultValue) {
        try {
            return getConfigBooleanValue(configType);
        } catch (IllegalStateException e) {
            return defaultValue;
        }
    }

    /**
     * Normalizes a Base URL by trimming, ensuring protocol prefix, and removing trailing slashes.
     *
     * @param url Input URL
     * @return Normalized Base URL
     */
    public static String normalizeBaseUrl(String url) {
        if (Strings.isNullOrEmpty(url)) {
            return "http://localhost:8080";
        }
        url = url.trim();
        // If protocol is missing, prepend http://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        // Strip trailing slashes
        url = url.replaceAll("/+$", "");
        return url;
    }

    /**
     * Returns the application base URL used for email links and external share links.
     * Priority:
     * 1. Database configuration (T_CONFIG: BASE_URL)
     * 2. Environment variable (DOCS_BASE_URL)
     * 3. System property (docs.base_url)
     * 4. Default to http://localhost:8080 (or detected port)
     *
     * @return Base URL
     */
    public static String getBaseUrl() {
        if (hasActiveEntityManager()) {
            try {
                ConfigDao configDao = new ConfigDao();
                Config config = configDao.getById(ConfigType.BASE_URL);
                if (config != null && !Strings.isNullOrEmpty(config.getValue())) {
                    return normalizeBaseUrl(config.getValue());
                }
            } catch (Exception ignored) {
            }
        }
        String envBaseUrl = System.getenv(Constants.BASE_URL_ENV);
        if (!Strings.isNullOrEmpty(envBaseUrl)) {
            return normalizeBaseUrl(envBaseUrl);
        }
        String propBaseUrl = System.getProperty("docs.base_url", System.getProperty("base_url"));
        if (!Strings.isNullOrEmpty(propBaseUrl)) {
            return normalizeBaseUrl(propBaseUrl);
        }
        String port = System.getProperty("jetty.http.port", "8080");
        return "http://localhost:" + port;
    }

    /**
     * Returns the application/sender name used in emails and notifications.
     * Priority:
     * 1. Database configuration (T_CONFIG: SMTP_FROM_NAME)
     * 2. Database configuration (T_CONFIG: THEME name, if not empty and not 'Teedy')
     * 3. Default to FormosaBearV
     *
     * @return Mail application name
     */
    public static String getMailAppName() {
        if (hasActiveEntityManager()) {
            try {
                ConfigDao configDao = new ConfigDao();
                Config fromNameConfig = configDao.getById(ConfigType.SMTP_FROM_NAME);
                if (fromNameConfig != null && !Strings.isNullOrEmpty(fromNameConfig.getValue())) {
                    return fromNameConfig.getValue().trim();
                }
                Config themeConfig = configDao.getById(ConfigType.THEME);
                if (themeConfig != null && !Strings.isNullOrEmpty(themeConfig.getValue())) {
                    try (JsonReader reader = Json.createReader(new StringReader(themeConfig.getValue()))) {
                        JsonObject themeJson = reader.readObject();
                        String themeName = themeJson.getString("name", null);
                        if (!Strings.isNullOrEmpty(themeName) && !"Teedy".equalsIgnoreCase(themeName.trim())) {
                            return themeName.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "FormosaBearV";
    }
}
