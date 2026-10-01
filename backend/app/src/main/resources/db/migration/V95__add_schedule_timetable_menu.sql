-- V95: 排课与上课目录下新增「课表总览」页面菜单（周/月/季课表总览）
--
-- 背景：「课表与点名」页（17）原先把日工作与周/月/季课表挤在一页，日期条双态、进页拉三份接口。
-- 本次把周/月/季课表拆到独立页面 /schedule-timetable，日工作留在 17；前端路由由
-- module-education 的 educationRoutes 注册（path schedule-timetable）。
--
-- id 避让：V92 已核对 139~199 段空闲；139（学员与班级目录）、140（导航编排）已占，
-- 本页取 141（执行前按 SELECT id FROM sys_menu WHERE id = 141 复核为空）。
-- sys_role_menu 取号沿用 V92 风格：1410000 + 角色 ID，仍然只加不乘（角色 id 有 8e18 量级的
-- 造数角色，相乘会溢出撞段），INSERT IGNORE 兜底防重放撞键。
--
-- 授权原则与 V37/V59 一致：目录/菜单可见性跟随权限码——凡角色持有 education:read，即补授该菜单，
-- 否则菜单树里没有这个节点，侧边栏不会出现入口。页面节点挂本模块读取码（education:read），
-- 勾选即授教务查看码；写操作仍由按钮节点 112/113 的码收口，本页不新增权限码。
--
-- 兼容自查：普通 DML，一条语句一条路径；无 CTE、无窗口函数、无 JSON_TABLE、无 IF( 函数、无 PREPARE；
-- 不建表不改列，不涉及排序规则漂移；INSERT IGNORE，可整脚本重放。

-- 1) 页面菜单（挂在目录 15「排课与上课」下，排在排课管理 16 与课表与点名 17 之间）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (141, 'admin', 15, 'education_timetable', '课表总览', 'menu', '/schedule-timetable', 'education/ScheduleTimetableView',
     'education:read', 'Calendar', 15, 1, 'active', '整周、整月、整季的课表网格总览，点空格去排课管理新增，点课次可直接执行；勾选即授教务查看码', 0);

-- 2) 授权：持有 education:read 的角色补授该菜单（uk(role_id, menu_id) 去重）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1410000 + r.id, r.id, 141, 0
FROM sys_role r
WHERE r.deleted = 0
  AND r.permission_codes LIKE '%education:read%';
