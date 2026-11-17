package com.sismics.docs.core.constant;

/**
 * Configuration parameters.
 *
 * @author jtremeaux
 */
public enum ConfigType {
    /**
     * Lucene directory storage type.
     */
    LUCENE_DIRECTORY_STORAGE,
    /**
     * Theme configuration.
     */
    THEME,

    /**
     * Guest login.
     */
    GUEST_LOGIN,

    /**
     * OCR enabled and extensible OCR provider settings.
     */
    OCR_ENABLED,
    OCR_ENGINE,
    OCR_FALLBACK_TESSERACT,
    OCR_AI_BASE_URL,
    OCR_AI_API_KEY,
    OCR_AI_MODEL,
    OCR_AI_TIMEOUT_SECONDS,
    OCR_AI_PROMPT,
    OCR_PDF_FORCE_AI,
    OCR_TESSERACT_PATH,

    /**
     * Default language.
     */
    DEFAULT_LANGUAGE,

    /**
     * Base URL for external links and emails.
     */
    BASE_URL,

    /**
     * File storage, deletion, duplication and encryption settings.
     */
    FILE_DELETE,
    FILE_DUPLICATE,
    FILE_DOWNLOAD,
    FILE_ENCRYPT,
    FILE_ENCRYPT_LOCKED,

    /**
     * Allow privileged users (administrators / tag_editor) to silently swap file versions in-place.
     */
    FILE_SWAP,

    /**
     * Allow non-admin users to view groups they do not belong to and their members.
     */
    GROUP_VIEW_ALL,

    /**
     * Document preview watermark settings.
     */
    PREVIEW_WATERMARK_ENABLED,
    PREVIEW_WATERMARK_DATE_FORMAT,

    /**
     * SMTP server configuration.
     */
    SMTP_HOSTNAME,
    SMTP_PORT,
    SMTP_FROM,
    SMTP_FROM_NAME,
    SMTP_USERNAME,
    SMTP_PASSWORD,
    SMTP_SECURITY,

    /**
     * Inbox scanning configuration.
     */
    INBOX_ENABLED,
    INBOX_HOSTNAME,
    INBOX_PORT,
    INBOX_STARTTLS,
    INBOX_USERNAME,
    INBOX_PASSWORD,
    INBOX_FOLDER,
    INBOX_TAG,
    INBOX_AUTOMATIC_TAGS,
    INBOX_DELETE_IMPORTED,

    /**
     * LDAP connection.
     */
    LDAP_ENABLED,
    LDAP_HOST,
    LDAP_PORT,
    LDAP_USESSL,
    LDAP_USESTARTTLS,
    LDAP_ADMIN_DN,
    LDAP_ADMIN_PASSWORD,
    LDAP_BASE_DN,
    LDAP_FILTER,
    LDAP_DEFAULT_EMAIL,
    LDAP_DEFAULT_STORAGE,
    LDAP_DEFAULT_GROUP,
    LDAP_DEFAULT_GROUPMEMBERSHIP,
    LDAP_DEFAULT_NAMEATTRIBUTE
}
