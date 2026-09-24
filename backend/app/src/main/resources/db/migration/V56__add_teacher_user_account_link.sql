-- 教师档案关联登录账号：jp_teacher 增加可空 user_id，与 V30 给 jp_student 加「归属用户」同构。
--
-- 口径（AGENTS「底座边界」）：账号是底座持有的「人」，教师档案是教务模块持有的教学身份，
-- 所以关联列只能长在业务表上，不给 sys_user_account 反向加 teacher_id。
-- 档案不强制有账号：兼职/离职教师可能永远不登录，且开账号要 system:user:manage 权限，
-- 与教师档案的 education:write 不是同一套，因此 user_id 允许为 NULL。
--
-- 唯一键不带 deleted：与 V30 的 uk_jp_student_user_id 保持一致；账号删除档案时
-- （TeacherMapper.softDeleteAndUnlink）会在同一条 UPDATE 里把 user_id 置空，
-- 已删行恒为 NULL，MySQL 唯一索引放行多个 NULL，不会出现「删过的档案还占着账号」。
--
-- 不加外键：账号注销走底座 UserAccountDeregisteredEvent → StudentDeregistrationListener 清关联，
-- 加了外键等于让底座的账号清理被业务表反向阻塞（V30 同口径）。
ALTER TABLE jp_teacher ADD COLUMN user_id BIGINT NULL COMMENT '关联登录账号ID(sys_user_account.id,NULL=尚未开通或未绑定账号)' AFTER campus_id;

ALTER TABLE jp_teacher ADD UNIQUE KEY uk_jp_teacher_user_id (user_id);
