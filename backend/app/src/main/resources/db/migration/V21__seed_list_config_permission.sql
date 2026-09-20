-- V21: Seed 列表配置 menu node + button permission for list config management
-- Menu id 7 follows V8(1-5)/V14(6); button id 107 follows V17(101-106);
-- sys_role_menu id 13/14 follows V8(1-5)/V14(6)/V17(7-12).

INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (7, 1, 'system_list_configs', '列表配置', 'menu', '/system/list-configs', 'system/list-configs',
     NULL, 'tools', 145, 1, 'active', '列表展示列与查询条件配置', 0);

INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (107, 7, 'system_list_configs_manage', '列表配置维护', 'button', NULL, NULL,
     'system:list-config:manage', NULL, 10, 0, 'active', '列表配置页操作权限', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (13, 1, 7, 0),
    (14, 1, 107, 0);

-- Append controller permission code for PLATFORM_ADMIN (guarded against duplicates)
UPDATE jp_role
SET permission_codes = CONCAT(permission_codes, ',system:list-config:manage')
WHERE role_code = 'PLATFORM_ADMIN'
  AND deleted = 0
  AND permission_codes IS NOT NULL
  AND permission_codes NOT LIKE '%system:list-config:manage%';
