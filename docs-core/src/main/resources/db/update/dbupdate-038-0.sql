-- Add FIL_VERSION_LABEL_C to T_FILE and preserve existing files' 1.0 / 2.0 version labels
alter table T_FILE add column FIL_VERSION_LABEL_C varchar(50);
update T_FILE set FIL_VERSION_LABEL_C = concat(cast(FIL_VERSION_N + 1 as varchar(10)), '.0') where FIL_VERSION_LABEL_C is null;

-- Update DB version to 38
update T_CONFIG set CFG_VALUE_C = '38' where CFG_ID_C = 'DB_VERSION';
