-- Add optional description column to T_TAG
alter table T_TAG add column TAG_DESCRIPTION_C varchar(200);

-- Update DB version to 47
update T_CONFIG set CFG_VALUE_C = '47' where CFG_ID_C = 'DB_VERSION';
