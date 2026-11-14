-- Add SHA_ALLOWEXPORT_B to T_SHARE (default false: preview only)
alter table T_SHARE add column SHA_ALLOWEXPORT_B boolean default false not null;

-- Update DB version to 40
update T_CONFIG set CFG_VALUE_C = '40' where CFG_ID_C = 'DB_VERSION';
