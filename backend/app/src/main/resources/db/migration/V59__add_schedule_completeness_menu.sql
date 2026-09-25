-- V59: 排课中心下新增「排课完成度」页面菜单(班级×周完成度报表)
--
-- id 避让:菜单 19 接续 V37 的 15-17 与 18(系统邀请码),19 之后直接是 101 段按钮;
-- sys_role_menu 用 5000 + role_id*10 + 序号 的显式算式取号(窗口函数在 Flyway 的 MySQL-5 解析器下禁用,
-- 不能照抄 V37 的 ROW_NUMBER 写法),角色 ID 在种子脚本里是固定小值(1/101/102),该式不会撞主键。
-- 授权原则与 V37 一致:目录/菜单可见性跟随权限码——凡角色持有 education:read,即补授该菜单,
-- 否则菜单树里没有这个节点,侧边栏不会出现入口。

-- 1) 页面菜单(挂在 V37 的顶层目录 15「排课中心」下,排在执行与课表之后)
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (19, 'admin', 15, 'education_completeness', '排课完成度', 'menu', '/schedule-completeness', 'education/ScheduleCompletenessView',
     NULL, 'DataAnalysis', 30, 1, 'active', '班级×自然周应排/已生成/已完成/缺口汇总报表', 0);

-- 2) 授权:持有 education:read 的角色补授该菜单(uk(role_id, menu_id) 去重)
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 5000 + r.id * 10 + 1, r.id, 19, 0
FROM sys_role r
WHERE r.deleted = 0
  AND r.permission_codes LIKE '%education:read%';
