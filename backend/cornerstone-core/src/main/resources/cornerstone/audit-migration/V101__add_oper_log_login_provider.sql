-- ============================================================================
-- V101：操作留痕增加登录渠道
--
-- 账号密码、微信小程序、第三方 OAuth2 都会写 LOGIN 记录，只有 provider 能区分。
-- 匿名/未知渠道留 NULL，不写占位值。
-- 双轨：教培侧见 backend/app/src/main/resources/db/audit-migration/V3__add_oper_log_login_provider.sql。
-- ============================================================================

ALTER TABLE `oper_log`
    ADD COLUMN `login_provider` VARCHAR(64) NULL COMMENT '登录渠道(如 password/wechat_miniapp/linuxdo;非登录操作为NULL)' AFTER `portal`;
