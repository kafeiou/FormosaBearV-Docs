-- Add DOT_ORDER_N to T_DOCUMENT_TAG
alter table T_DOCUMENT_TAG add column DOT_ORDER_N integer;

-- Ghost cleanup: Clean orphan associations where document or tag was already deleted
update T_DOCUMENT_TAG set DOT_DELETEDATE_D = current_timestamp where DOT_DELETEDATE_D is null and DOT_IDDOCUMENT_C in (select d.DOC_ID_C from T_DOCUMENT d where d.DOC_DELETEDATE_D is not null);

update T_DOCUMENT_TAG set DOT_DELETEDATE_D = current_timestamp where DOT_DELETEDATE_D is null and DOT_IDTAG_C in (select t.TAG_ID_C from T_TAG t where t.TAG_DELETEDATE_D is not null);

-- Update DB version to 34
update T_CONFIG set CFG_VALUE_C = '34' where CFG_ID_C = 'DB_VERSION';
