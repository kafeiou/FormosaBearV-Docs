-- Add TAG_INHERIT_ACL_B to T_TAG (default true for hierarchical inheritance)
alter table T_TAG add column TAG_INHERIT_ACL_B boolean default true not null;

-- Update DB version to 35
update T_CONFIG set CFG_VALUE_C = '35' where CFG_ID_C = 'DB_VERSION';
