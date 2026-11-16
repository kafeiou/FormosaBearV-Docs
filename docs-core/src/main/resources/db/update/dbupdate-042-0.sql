-- Add USE_NAME_C to T_USER for optional user display name
alter table T_USER add column USE_NAME_C varchar(100);

-- Add LDC_DEFAULT_NAMEATTR_C to T_LDAP_CONFIG for configurable LDAP name attribute (default 'cn')
alter table T_LDAP_CONFIG add column LDC_DEFAULT_NAMEATTR_C varchar(250) default 'cn';

-- Update DB version to 42
update T_CONFIG set CFG_VALUE_C = '42' where CFG_ID_C = 'DB_VERSION';
