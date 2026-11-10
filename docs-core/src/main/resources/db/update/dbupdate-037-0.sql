-- Add DOC_ALLOW_DOWNLOAD_B to T_DOCUMENT (default false)
alter table T_DOCUMENT add column DOC_ALLOW_DOWNLOAD_B boolean default false not null;

-- Update DB version to 37
update T_CONFIG set CFG_VALUE_C = '37' where CFG_ID_C = 'DB_VERSION';
