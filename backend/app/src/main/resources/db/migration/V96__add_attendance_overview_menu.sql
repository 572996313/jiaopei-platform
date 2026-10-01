-- V96: 排课与上课目录下新增「点名总览」页面菜单（按班级汇总的点名情况展示）
--
-- 背景：点名在「课表与点名」(17) 一节一节点，点完之后没有一个地方回答"每个班今天点什么情况了"。
-- 本次新增独立页面 /attendance-overview：按日期把课次聚到班级一行，展示点名进度与出勤/迟到/请假/缺勤
-- 人次；前端路由由 module-education 的 educationRoutes 注册（path attendance-overview）。
-- 页面只做展示与补点入口，复用既有接口（日课表 + 课次点名进度批量），后端不新增端点。
--
-- id 避让：V95 已核对 142~199 空闲（200 起是小程序端节点），141（课表总览）已占，
-- 本页取 142（执行前按 SELECT id FROM sys_menu WHERE id = 142 复核为空）。
-- sys_role_menu 取号沿用 V95 风格：1420000 + 角色 ID（本页）、1421000 + 角色 ID（第 3 段给 141 补授），
-- 仍然只加不乘（角色 id 有 8e18 量级的造数角色，相乘会溢出撞段），INSERT IGNORE 兜底防重放撞键。
--
-- 排序：目录 15 下现有 排课管理(10) / 课表总览(15) / 课表与点名(20) / 课时完成度(30)，
-- 本页 sort_no=25，排在「课表与点名」之后——先点名、再看汇总，同一条链按操作顺序排。
--
-- 授权口径与 V95 不同，这里是修正不是沿用：V95 只按 permission_codes CSV 匹配持有者，而 V74 起预设
-- 角色（机构运营/教师）的 education:read 只来自已授页面节点、CSV 留空——结果 V95 的 141 没授给它们，
-- 机构运营在侧边栏看不到「课表总览」。本页持有者判定改为"CSV 或已授节点"两个来源取并集：
--   CSV 来源  sys_role.permission_codes LIKE '%education:read%'
--   节点来源  sys_role_menu 已授的页面节点里存在 permission_code = 'education:read' 的行
-- 第 3 段用同一口径给 141 补授（V95 已发过的脚本不改，缺口在这里收口）。
-- 页面节点挂本模块读取码（education:read），勾选即授教务查看码；点名写操作仍由按钮节点 113 的码收口，
-- 本页不新增权限码。
--
-- 兼容自查：普通 DML，一条语句一条路径；无 CTE、无窗口函数、无 JSON_TABLE、无 IF( 函数、无 PREPARE；
-- 不建表不改列，不涉及排序规则漂移；INSERT IGNORE，可整脚本重放。

-- 1) 页面菜单（挂在目录 15「排课与上课」下，排在课表与点名 20 与课时完成度 30 之间）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (142, 'admin', 15, 'education_attendance_overview', '点名总览', 'menu', '/attendance-overview', 'education/AttendanceOverviewView',
     'education:read', 'Finished', 25, 1, 'active', '按班级汇总看每天点名了没有、出勤缺勤多少；点开班级行可补点；勾选即授教务查看码', 0);

-- 2) 授权：持有 education:read 的角色补授 142（CSV 或已授页面节点，两个来源取并集；
--    DISTINCT 兜底：同一角色两个来源都命中时只出一行，取号式相同才不会撞主键）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1420000 + r.id, r.id, 142, 0
FROM sys_role r
WHERE r.deleted = 0
  AND (
    r.permission_codes LIKE '%education:read%'
    OR EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      JOIN sys_menu m ON m.id = rm.menu_id
      WHERE rm.role_id = r.id
        AND rm.deleted = 0
        AND m.deleted = 0
        AND m.permission_code = 'education:read'
    )
  );

-- 3) 兜底：V95 按 CSV 授 141 时漏了"节点来源"的角色（机构运营/教师），同口径补授
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1421000 + r.id, r.id, 141, 0
FROM sys_role r
WHERE r.deleted = 0
  AND EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.id = rm.menu_id
    WHERE rm.role_id = r.id
      AND rm.deleted = 0
      AND m.deleted = 0
      AND m.permission_code = 'education:read'
  );
