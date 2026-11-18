-- Create plugin platform tables (T_PLUGIN, T_PLUGIN_TAG, T_PLUGIN_DATA)
create table if not exists T_PLUGIN ( PLG_ID_C varchar(64) not null, PLG_NAME_C varchar(100) not null, PLG_VERSION_C varchar(36) not null, PLG_DESCRIPTION_C varchar(500), PLG_ENTRY_C varchar(255) not null, PLG_NAVLABEL_C varchar(100), PLG_NAVICON_C varchar(64), PLG_HANDLER_C varchar(64), PLG_MANIFEST_C longvarchar, PLG_ENABLED_B bit not null, PLG_CREATEDATE_D datetime not null, PLG_UPDATEDATE_D datetime not null, primary key (PLG_ID_C) );
create table if not exists T_PLUGIN_TAG ( PTG_ID_C varchar(36) not null, PTG_IDPLUGIN_C varchar(64) not null, PTG_IDTAG_C varchar(36) not null, PTG_ROLE_C varchar(32) not null, primary key (PTG_ID_C) );
create table if not exists T_PLUGIN_DATA ( PLD_ID_C varchar(36) not null, PLD_IDPLUGIN_C varchar(64) not null, PLD_COLLECTION_C varchar(64) not null, PLD_KEY_C varchar(128) not null, PLD_IDUSER_C varchar(36), PLD_VALUE_C longvarchar not null, PLD_CREATEDATE_D datetime not null, PLD_UPDATEDATE_D datetime not null, primary key (PLD_ID_C) );

-- Update DB version to 45
update T_CONFIG set CFG_VALUE_C = '45' where CFG_ID_C = 'DB_VERSION';
