-- Add optional description column to T_GROUP
alter table T_GROUP add column GRP_DESCRIPTION_C varchar(200);
update T_GROUP set GRP_DESCRIPTION_C = 'Online Exam subject teachers (create and manage exams)' where GRP_NAME_C = 'ExamTeachers' and GRP_DESCRIPTION_C is null;
update T_GROUP set GRP_DESCRIPTION_C = 'Online Exam examinees (take assigned exams)' where GRP_NAME_C = 'ExamStudents' and GRP_DESCRIPTION_C is null;

-- Update DB version to 46
update T_CONFIG set CFG_VALUE_C = '46' where CFG_ID_C = 'DB_VERSION';
