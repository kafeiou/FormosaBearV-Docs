-- Remove unintended admin role from tag_editor group so tag_editor members do not inherit BaseFunction.ADMIN
update T_GROUP set GRP_IDROLE_C = null where GRP_ID_C = 'tag_editor' or GRP_NAME_C = 'tag_editor';

-- Update DB version to 43
update T_CONFIG set CFG_VALUE_C = '43' where CFG_ID_C = 'DB_VERSION';
