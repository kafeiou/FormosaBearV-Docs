alter table T_TAG add column TAG_ORDER_N integer;
update T_CONFIG set CFG_VALUE_C = '33' where CFG_ID_C = 'DB_VERSION';
