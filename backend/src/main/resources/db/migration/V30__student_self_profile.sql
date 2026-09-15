-- 学生档案归属:jp_student 加 user_id 表示"该档案归哪个学生账号所有",
-- 唯一索引保证一个账号只能归属一个档案(多个 NULL 不受影响,后台创建的未归属档案兼容)。
-- 注销时清空 user_id、删除关联;关联表 jp_user_student_rel 中的学生归属行在注销时一并清理。

ALTER TABLE jp_student ADD COLUMN user_id BIGINT NULL AFTER campus_id;
ALTER TABLE jp_student ADD UNIQUE KEY uk_jp_student_user_id (user_id);