-- Add USE_AUTH_SOURCE_C to T_USER (default 'INTERNAL')
alter table T_USER add column USE_AUTH_SOURCE_C varchar(20) default 'INTERNAL' not null;

-- Add BASE_URL and SMTP_FROM_NAME to T_CONFIG
insert into T_CONFIG (CFG_ID_C, CFG_VALUE_C) values ('BASE_URL', 'http://localhost:8080');
insert into T_CONFIG (CFG_ID_C, CFG_VALUE_C) values ('SMTP_FROM_NAME', 'FormosaBearV');

-- Update DB version to 36
update T_CONFIG set CFG_VALUE_C = '36' where CFG_ID_C = 'DB_VERSION';

