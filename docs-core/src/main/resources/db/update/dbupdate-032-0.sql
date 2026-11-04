-- DBUPDATE-032-0.SQL

-- Insert a new group that can create tag
insert into T_GROUP(GRP_ID_C, GRP_NAME_C, GRP_IDROLE_C) values('tag_editor', 'tag_editor', 'admin');
insert into T_USER_GROUP(UGP_ID_C, UGP_IDUSER_C, UGP_IDGROUP_C) values('admin-tag_editor', 'admin', 'tag_editor');

-- Update the database version
update T_CONFIG set CFG_VALUE_C = '32' where CFG_ID_C = 'DB_VERSION';
