-- V36: 系统设置菜单重组——10 个平铺菜单归入 3 个二级目录(机构与权限/低代码配置/系统运维)
--
-- id 避让:菜单 12-14 follows V33(11)与 V29(200+);sys_role_menu 使用 1000+ 段(既有用 1-22/200-215)。
-- 二级目录 path/component/permission_code 为空,仅作分组容器;页面菜单仍挂其下,按钮节点不动。
-- 授权原则:目录可见性跟随子项——凡持有某组任一子菜单的角色,自动获得该目录节点授权,
-- 否则后端 buildTree 会把子项提升为根节点,侧边栏分组失效。

-- 1) 新增二级目录节点(portal=admin,parent=1 系统设置)
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (12, 'admin', 1, 'system_org_permission', '机构与权限', 'catalog', NULL, NULL,
     NULL, 'Lock', 110, 1, 'active', '机构资料与用户角色菜单权限配置', 0),
    (13, 'admin', 1, 'system_lowcode_config', '低代码配置', 'catalog', NULL, NULL,
     NULL, 'DataLine', 120, 1, 'active', '字典/数据源/列表配置等元数据配置', 0),
    (14, 'admin', 1, 'system_ops', '系统运维', 'catalog', NULL, NULL,
     NULL, 'Monitor', 130, 1, 'active', '系统参数与操作留痕', 0);

-- 2) 重挂既有页面菜单到二级目录,并重排组内顺序
UPDATE sys_menu SET parent_id = 12, sort_no = 10 WHERE id = 8;   -- 机构资料
UPDATE sys_menu SET parent_id = 12, sort_no = 20 WHERE id = 2;   -- 用户管理
UPDATE sys_menu SET parent_id = 12, sort_no = 30 WHERE id = 3;   -- 角色管理
UPDATE sys_menu SET parent_id = 12, sort_no = 40 WHERE id = 4;   -- 菜单管理
UPDATE sys_menu SET parent_id = 12, sort_no = 50 WHERE id = 9;   -- 功能权限
UPDATE sys_menu SET parent_id = 13, sort_no = 10 WHERE id = 5;   -- 字典管理
UPDATE sys_menu SET parent_id = 13, sort_no = 20 WHERE id = 11;  -- 数据源配置
UPDATE sys_menu SET parent_id = 13, sort_no = 30 WHERE id = 7;   -- 列表配置
UPDATE sys_menu SET parent_id = 14, sort_no = 10 WHERE id = 6;   -- 系统参数
UPDATE sys_menu SET parent_id = 14, sort_no = 20 WHERE id = 10;  -- 操作日志

-- 3) 目录授权:凡持有组内任一子菜单的角色,补授权该组目录节点(uk(role_id,menu_id) 去重)
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1000 + ROW_NUMBER() OVER (ORDER BY rm.role_id, dir_map.dir_id), rm.role_id, dir_map.dir_id, 0
FROM sys_role_menu rm
JOIN (
              SELECT  2 AS child_id, 12 AS dir_id
    UNION ALL SELECT  3, 12
    UNION ALL SELECT  4, 12
    UNION ALL SELECT  8, 12
    UNION ALL SELECT  9, 12
    UNION ALL SELECT  5, 13
    UNION ALL SELECT  7, 13
    UNION ALL SELECT 11, 13
    UNION ALL SELECT  6, 14
    UNION ALL SELECT 10, 14
) dir_map ON dir_map.child_id = rm.menu_id
WHERE rm.deleted = 0;
