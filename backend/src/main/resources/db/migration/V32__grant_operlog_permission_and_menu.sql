-- V32: 操作留痕日志查询页——权限码 + 菜单节点
-- 操作日志表 oper_log 建在独立日志库 jiaopei_log(见 db/audit-migration/V1__,由 auditFlyway 单独迁移),
-- 本迁移仅在业务库追加「操作日志」页面菜单与 PLATFORM_ADMIN 的查询权限码。
--
-- id 避让:菜单 10 follows V8(1-5)/V14(6)/V23(8-9);按钮 110 follows V17(101-106)/V21(107)/V23(108-109);
-- sys_role_menu 19-20 follows V8(1-5)/V14(6)/V17(7-12)/V21(13-14)/V23(15-18)。

-- 1) 页面级菜单节点(visible=1):操作日志(管理后台 portal=admin)
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (10, 'admin', 1, 'system_oper_log', '操作日志', 'menu', '/system/operlogs', 'system/operlogs',
     NULL, 'list', 170, 1, 'active', '操作留痕日志查询', 0);

-- 2) 按钮节点(hidden,承载页面查看所需权限码,parent 指向页面)
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (110, 'admin', 10, 'system_oper_log_view', '操作日志查看', 'button', NULL, NULL,
     'system:operlog:read', NULL, 10, 0, 'active', '操作日志页可见性所需权限', 0);

-- 3) 角色授权:PLATFORM_ADMIN(role_id=1)勾选新节点
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (19, 1, 10, 0),
    (20, 1, 110, 0);

-- 4) 追加控制器权限码到 PLATFORM_ADMIN 角色(与既有权限合并,避免重复)
UPDATE jp_role
SET permission_codes = TRIM(BOTH ',' FROM CONCAT_WS(',', permission_codes, 'system:operlog:read'))
WHERE role_code = 'PLATFORM_ADMIN'
  AND deleted = 0
  AND (permission_codes IS NULL OR permission_codes NOT LIKE '%system:operlog:read%');