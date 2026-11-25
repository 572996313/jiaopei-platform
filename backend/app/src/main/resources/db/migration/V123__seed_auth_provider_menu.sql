-- ============================================================================
-- 第三方登录渠道配置：菜单节点与角色授权（教培侧真正执行的那份）。
--
-- 双轨：底座基线侧内容一字不差，见
--   backend/cornerstone-core/src/main/resources/cornerstone/migration/V123__seed_auth_provider_menu.sql
--
-- 菜单 id 124：接在 101~123 之后，挂在「机构与权限」目录 12 下（登录方式属于账号与权限）。
-- 该页读与写共用 system:auth-provider:manage（V62/V105 口径：同码只挂页面节点，不再配同名按钮）。
-- 授权口径：只授平台管理员(1)。渠道配置是跨机构的平台侧设置，机构运营(110)默认不给，
-- 需要时由平台在「角色管理」里勾选。
-- 权限由菜单节点携带：UserAccessTokenService 会把已授权菜单节点的 permission_code
-- 合并进权限快照，因此不必再 UPDATE sys_role.permission_codes。
-- ============================================================================

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (124, 'admin', 12, 'system_auth_providers', '第三方登录', 'menu', '/system/auth-providers',
     'system/auth-providers', 'system:auth-provider:manage', 'Key', 60, 1, 'active',
     '第三方登录渠道配置页；勾选即授该码(该页读与写共用一个码)。密钥写入即加密，界面只显示是否已配置', 0);

-- 页面节点：只授平台管理员
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1240001, 1, 124, 0);
