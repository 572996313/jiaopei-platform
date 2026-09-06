-- V17: Seed button-type menu nodes carrying permission codes (phase 1: system domain)
-- Target model: menu nodes (catalog/menu) drive visibility only; button nodes
-- (menu_type=button, visible=0) carry the API permission codes. Effective role
-- permissions = role CSV codes UNION granted menu/button node permission codes.

-- Menu nodes no longer carry permission codes; visibility is grant-driven,
-- API authorization is button-driven. Also clears the dead V14 code
-- 'system:config:manage' (no controller uses it; controllers use read/write).
UPDATE sys_menu
SET permission_code = NULL
WHERE id IN (2, 3, 4, 5, 6)
  AND deleted = 0;

-- Button nodes under existing system menus
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (101, 2, 'system_users_manage',  '用户维护', 'button', NULL, NULL, 'system:user:manage',  NULL, 10, 0, 'active', '用户管理页操作权限', 0),
    (102, 3, 'system_roles_manage',  '角色维护', 'button', NULL, NULL, 'system:role:manage',  NULL, 10, 0, 'active', '角色管理页操作权限', 0),
    (103, 4, 'system_menus_manage',  '菜单维护', 'button', NULL, NULL, 'system:menu:manage',  NULL, 10, 0, 'active', '菜单管理页操作权限', 0),
    (104, 5, 'system_dicts_manage',  '字典维护', 'button', NULL, NULL, 'system:dict:manage',  NULL, 10, 0, 'active', '字典管理页操作权限', 0),
    (105, 6, 'system_config_read',   '参数查看', 'button', NULL, NULL, 'system:config:read',  NULL, 10, 0, 'active', '系统参数查看权限', 0),
    (106, 6, 'system_config_write',  '参数维护', 'button', NULL, NULL, 'system:config:write', NULL, 20, 0, 'active', '系统参数维护权限', 0);

-- Grant all button nodes to PLATFORM_ADMIN (explicit ids; table has no auto-increment)
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (7,  1, 101, 0),
    (8,  1, 102, 0),
    (9,  1, 103, 0),
    (10, 1, 104, 0),
    (11, 1, 105, 0),
    (12, 1, 106, 0);
