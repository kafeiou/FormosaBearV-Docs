alter table T_TAG add column TAG_HIDDEN_B boolean default false not null;
update T_CONFIG set CFG_VALUE_C = '45' where CFG_ID_C = 'DB_VERSION';
