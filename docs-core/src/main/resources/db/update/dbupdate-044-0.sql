alter table T_TAG add column if not exists TAG_HIDDEN_B boolean default false not null;
update T_CONFIG set CFG_VALUE_C = '44' where CFG_ID_C = 'DB_VERSION';
