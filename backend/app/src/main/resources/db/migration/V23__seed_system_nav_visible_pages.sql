-- V23: 补齐「系统设置」目录下两个页面级菜单节点(机构资料 / 功能权限),
-- 使侧边栏「系统设置」整组由 sys_menu 驱动渲染(名称/顺序/图标/授权见 menuName/sortNo/icon/role_menu)。
-- 同时把该目录下全部页面子节点 sort_no 统一为间隔 10 的序列,便于在菜单管理页插入空位调整顺序。
--
-- id 避让:菜单 8/9 follows V8(1-5)/V14(6)/V21(7);按钮 108/109 follows V17(101-106)/V21(107);
-- sys_role_menu 15-18 follows V8(1-5)/V14(6)/V17(7-12)/V21(13-14)。

-- 1) 页面级菜单节点(visible=1):机构资料 / 功能权限
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (8, 1, 'system_institution', '机构资料', 'menu', '/system/institution', 'system/institution',
     NULL, 'office-building', 160, 1, 'active', '机构基础资料维护', 0),
    (9, 1, 'system_permission_overview', '功能权限', 'menu', '/system/permission-overview', 'system/permission-overview',
     NULL, 'lock', 180, 1, 'active', '权限码与受控接口总览', 0);

-- 2) 按钮节点(hidden,承载页面查看所需权限码,parent 指向对应页面)
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (108, 8, 'system_institution_view', '机构资料查看', 'button', NULL, NULL,
     'organization:read', NULL, 10, 0, 'active', '机构资料页可见性所需权限', 0),
    (109, 9, 'system_permission_overview_view', '功能权限查看', 'button', NULL, NULL,
     'system:menu:manage', NULL, 10, 0, 'active', '功能权限总览页可见性所需权限', 0);

-- 3) 角色授权:PLATFORM_ADMIN(role_id=1)勾选新节点
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (15, 1, 8, 0),
    (16, 1, 108, 0),
    (17, 1, 9, 0),
    (18, 1, 109, 0);

-- 4) 统一「系统设置」目录(menu id 2-9)sort_no 为间隔 10 序列,保留 110~180 空位供调整
UPDATE sys_menu
SET sort_no = CASE id
        WHEN 2 THEN 110
        WHEN 3 THEN 120
        WHEN 4 THEN 130
        WHEN 5 THEN 140
        WHEN 7 THEN 150
        WHEN 8 THEN 160
        WHEN 9 THEN 180
        ELSE sort_no
    END
WHERE id IN (2, 3, 4, 5, 7, 8, 9)
  AND deleted = 0;

-- 5) 对齐顶层目录显示名:侧边栏该分组原展示「系统设置」,现目录名由 sys_menu 驱动,
--    故将 system 目录名统一为「系统设置」(V16 曾汉化为「系统管理」,仅展示字段;后续可在菜单管理里修改)
UPDATE sys_menu
SET menu_name = '系统设置'
WHERE id = 1
  AND deleted = 0;
