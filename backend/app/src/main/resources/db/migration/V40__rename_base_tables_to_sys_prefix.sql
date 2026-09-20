-- V40: 底座表统一 sys_ 前缀
-- 阶段四把包名、配置前缀、Redis key 都统一成 cornerstone，只有底座表名还带着业务前缀 jp_，
-- 与"jp_ = 教培业务表"的口径混在一起：第二个项目拿到 cornerstone-core，看到的却是
-- jp_user_account，分不清哪张表属于底座、哪张属于自己的业务域。本迁移只改表名，不动结构、不动数据。
--
-- 两点约束（改这张清单前先看）：
-- 1) 索引/键名（uk_jp_*、fk_jp_*、chk_jp_*）保持原样：键依附在表上，逐表 DROP/ADD INDEX 对外键列
--    有 1553 风险且收益为零，按历史遗留处理，AGENTS.md 已注明"键名不随表改名"。
-- 2) MySQL 的 RENAME TABLE 会自动把子表外键指向新表名，不需要先断外键再回挂。
--
-- 本迁移不建表也不删表：底座 auth 包下曾有 UserTokenEntity/UserTokenMapper 指向 jp_user_token，
-- 该表早在 V14 就被 DROP（登录态改由 Redis 会话承载），这两个类是无人调用的死代码，随本次改名一并删除。

RENAME TABLE jp_institution TO sys_institution,
             jp_user_account TO sys_user_account,
             jp_role TO sys_role,
             jp_user_role_rel TO sys_user_role_rel;

-- 两张业务表的 user_id 注释按旧表名写了出处，改完与实体的 @Schema 对齐
-- （列定义原样照抄，MODIFY COLUMN 漏写类型/NULL 会被 MySQL 隐式改列）。
ALTER TABLE jp_user_student_rel MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户ID(sys_user_account.id)';
ALTER TABLE jp_wechat_binding MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '绑定的系统用户ID(sys_user_account.id)';
