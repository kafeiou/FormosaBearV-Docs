-- Add LDC_USESTARTTLS_B to T_LDAP_CONFIG for STARTTLS protocol support
alter table T_LDAP_CONFIG add column LDC_USESTARTTLS_B boolean default false not null;

-- Update DB version to 41
update T_CONFIG set CFG_VALUE_C = '41' where CFG_ID_C = 'DB_VERSION';
