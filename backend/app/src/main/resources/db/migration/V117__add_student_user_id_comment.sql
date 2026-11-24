-- V117: 补 jp_student.user_id 的中文注释。
--
-- 背景：V30 给学生档案加 user_id 时漏写 COMMENT（AGENTS.md「数据库表 / 字段中文注释规范」要求
-- 加列必须有注释），数据字典页上线后这条被"缺注释字段数"统计扫出来，在这里补上。
--
-- 只用 MODIFY COLUMN 补注释：类型 / NULL / 默认值与 V30 完全一致，不带位置子句（列位置保持现状）。
ALTER TABLE `jp_student`
    MODIFY COLUMN `user_id` BIGINT NULL COMMENT '归属登录账号ID(sys_user_account.id,NULL=未绑定学生账号)';
