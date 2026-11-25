-- ============================================================================
-- V122：第三方登录渠道种子
--
-- LinuxDo：端点与用户字段映射已填好，填上 client_id/client_secret/redirect_uri
--           并把 status 改为 active 即启用。初始停用，避免未配置密钥时登录页出现死按钮。
-- 微信小程序：只用于身份列表展示渠道名；换票由教务模块的 /api/auth/wechat 直接实现，
--           不在网页登录页出现（status 停用 + 底座无该类型实现，双保险）。
--
-- redirect_uri 必须与渠道后台登记的完全一致。生产同源部署时填
--   https://<域名>/api/auth/oauth2/linuxdo/callback
-- 开发环境前端在 5173、后端在 9080，建议填前端源再靠 Vite 代理转发：
--   http://127.0.0.1:5173/api/auth/oauth2/linuxdo/callback
-- 若回调要直接打到别的源，在 extra_config 里加 "webBaseUrl" 指定前端页面基地址。
-- ============================================================================

INSERT INTO sys_auth_provider (
    id, provider_code, provider_name, provider_type, icon, sort_no, status,
    client_id, client_secret, authorize_url, token_url, userinfo_url, scope,
    token_auth_method, redirect_uri, extra_config, remark, deleted
) VALUES
    (1, 'linuxdo', 'LinuxDo', 'OAUTH2', 'linuxdo', 10, 'disabled',
     NULL, NULL,
     'https://connect.linux.do/oauth2/authorize',
     'https://connect.linux.do/oauth2/token',
     'https://connect.linux.do/api/user',
     'user', 'post', NULL,
     '{"userIdField":"id","nicknameField":"name","avatarField":"avatar_template"}',
     'LinuxDo Connect 单点登录；填 client_id/client_secret/redirect_uri 后改为 active', 0),
    (2, 'wechat_miniapp', '微信小程序', 'WECHAT_MINIAPP', 'wechat', 20, 'disabled',
     NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     '微信小程序一键登录（由教务模块直接实现，网页端不展示）', 0);
