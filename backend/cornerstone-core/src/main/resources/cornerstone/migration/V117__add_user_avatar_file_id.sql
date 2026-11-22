-- ============================================================================
-- 用户头像：sys_user_account 增加 avatar_file_id，并种入头像大小上限参数。
--
-- 双轨：本文件是底座基线侧那份；教培侧真正执行的那份内容一字不差，见
--   backend/app/src/main/resources/db/migration/V114__add_user_avatar_file_id.sql
-- 教培库只跑 app 侧 V1xx，下游从零建库只跑底座 V1xx，两份都要有。
--
-- 设计口径：
--   1. 只存 sys_file.id，不存对象键 / bucket / URL（文件模块契约）；
--   2. 不加外键：sys_file 行是物理删除，外键会挡住删文件；
--   3. 头像文件必须按 (biz_type='user_avatar', biz_id=用户ID) 绑定，否则孤儿清理
--      会在 file.orphan.retention_hours 之后把头像删掉；
--   4. 账号注销时由 UserAvatarCleanupListener 释放绑定，避免文件永久挂在已删除的 bizId 上。
-- ============================================================================

ALTER TABLE `sys_user_account`
    ADD COLUMN `avatar_file_id` BIGINT DEFAULT NULL
        COMMENT '头像文件ID(sys_file.id,NULL=未设置头像)' AFTER `remark`;

INSERT IGNORE INTO sys_system_config (
    id, config_key, config_value, value_type, config_group, display_name, description,
    default_value, is_system, sort_order, status, deleted
) VALUES
    (19, 'user.avatar.max_size_mb', '2', 'int', 'system', '头像大小上限(MB)',
     '个人头像单文件允许的最大体积；留空或 0 表示不限制。头像只接受 JPG/PNG 格式',
     '2', 0, 40, 'active', 0);
