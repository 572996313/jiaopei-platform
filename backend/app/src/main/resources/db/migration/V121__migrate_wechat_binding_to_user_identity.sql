-- ============================================================================
-- V121：把存量微信小程序绑定迁入通用身份表，并删除旧表
--
-- 旧表 jp_wechat_binding 的 openid 全局唯一，一个微信只能绑一个账号；
-- 新表 sys_user_identity 允许同一外部身份绑定多个账号（家长 + 员工），
-- 登录时由前端选账号。迁移只搬未删除的行，历史墓碑随旧表一起丢弃。
--
-- 先迁数据再删表：旧表的外键指向 sys_user_account，删子表本身不会触发 1451；
-- 但代码侧必须同批改成读写新表，否则重启后会查到已删除的表。
-- ============================================================================

INSERT INTO sys_user_identity (
    id, provider_code, app_type, open_id, union_id, user_id,
    nickname, avatar_url, last_login_at,
    created_at, created_by, updated_at, updated_by, deleted
)
SELECT
    id, 'wechat_miniapp', 'miniapp', openid, union_id, user_id,
    NULL, NULL, last_login_at,
    created_at, created_by, updated_at, updated_by, 0
FROM jp_wechat_binding
WHERE deleted = 0;

DROP TABLE jp_wechat_binding;
