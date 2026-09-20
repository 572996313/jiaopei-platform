-- V37: 排课与执行拆分——新增「排课中心」顶层目录与 排课管理/执行与课表 两个页面菜单
--
-- id 避让:菜单 15-17 避开已用 1-14(V33/V36)与 200+(学生/家长端);按钮 112-113 接续 101-111;
-- sys_role_menu 使用 2000+ 段(既有用 1-22/200-215/1000+)。
-- 目录 path/component/permission_code 为空,仅作分组容器;页面菜单挂其下,按钮节点承载权限码。
-- 授权原则:目录可见性跟随子项——凡 jp_role.permission_codes 含 education:read/education:write 的角色,
-- 自动获得目录+菜单+按钮授权,否则后端 buildTree 会把子菜单提升为根节点,侧边栏分组失效。

-- 1) 顶层目录 + 页面菜单 + 按钮权限节点
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (15, 'admin', NULL, 'education_schedule_center', '排课中心', 'catalog', NULL, NULL,
     NULL, 'Calendar', 40, 1, 'active', '排课与执行相关功能的顶层目录', 0),
    (16, 'admin', 15, 'education_scheduling', '排课管理', 'menu', '/scheduling', 'education/ScheduleManageView',
     NULL, 'Calendar', 10, 1, 'active', '排课计划维护(后续承接批量/周期排课)', 0),
    (17, 'admin', 15, 'education_execution', '执行与课表', 'menu', '/schedule', 'education/ScheduleAttendanceView',
     NULL, 'AlarmClock', 20, 1, 'active', '点名结课与日/周月季课表回查', 0),
    (112, 'admin', 16, 'education_scheduling_write', '排课维护', 'button', NULL, NULL,
     'education:write', NULL, 10, 0, 'active', '排课管理页新增/编辑/删除排课权限', 0),
    (113, 'admin', 17, 'education_execution_write', '执行变更', 'button', NULL, NULL,
     'education:write', NULL, 10, 0, 'active', '执行与课表页点名/结课/调课/停课/补课权限', 0);

-- 2) 授权:凡持有教育模块读/写权限码的角色,补授目录、菜单与按钮节点(uk(role_id,menu_id) 去重)
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 2000 + ROW_NUMBER() OVER (ORDER BY r.id, m.menu_id), r.id, m.menu_id, 0
FROM jp_role r
JOIN (
              SELECT  15 AS menu_id
    UNION ALL SELECT  16
    UNION ALL SELECT  17
    UNION ALL SELECT 112
    UNION ALL SELECT 113
) m
WHERE r.deleted = 0
  AND (r.permission_codes LIKE '%education:read%' OR r.permission_codes LIKE '%education:write%');
