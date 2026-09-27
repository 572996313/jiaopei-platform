-- V81: 「机构与教学资源」一页拆三页，菜单开「教学资源」目录承载
--
-- 背景：原 /campus 一页里叠了校区、教室、学期、时间段四张表，顶部还要一条校区筛选栏，
-- 每张表都只留出一小截高度，列铺不开。现按范围拆开：
--   /campus       校区与学期（机构级：校区列表 + 学期列表 + 校区默认账号）
--   /classrooms   教室管理（校区级一张主表，校区必选——教室必须挂在某个校区下）
--   /time-slots   节次与时间模板（原「时间段列表」卡并进来，页面顶部本来就有校区筛选）
-- 后端接口一个字没改，只是取数入口从一页挪到三页。
--
-- id 避让：菜单 130/131（现有号段是 1~25、101~129、200~212 的小程序端目录与页面，
-- 130~199 整段空闲，已按 SELECT id>=130 核对）；
-- sys_role_menu 用 1300000/1310000 + 角色 ID 取号（本表现有 id 是 200~7124 的种子段、
-- 2.1e18 的后台授权段与 8.000000000010e18 的造数段，130 万这一档整段为空，已核对）。
-- 用加法不用乘法：角色 id 里有 8e18 量级的造数角色，×10 会溢出 BIGINT。
--
-- 必须一起做的两件事：
--   1) 目录节点 130 要授给**所有**在职角色。菜单树是按角色已授权节点拼的，子页的父目录没被勾到时
--      会被当成孤儿提升到根，而侧边栏「教学资源」入口靠 menuCatalogCode 找目录节点取子项——
--      目录没授权 = 三张页面的入口整块消失（不是少一个分组，是点不进去）。
--   2) 新页面节点 131 自带 organization:read（读码挂页面节点，勾页面=入口可见+能读）。
--      不再给教室页配第三个 organization:write 按钮节点：写码挂按钮，但同一个码配三个节点
--      只是让操作员以为"勾一个不够"，116 那一条继续覆盖三张页面。

-- 1) 目录节点（catalog 永不挂权限码，见「权限码与菜单节点的分配规则」第 3 条）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (130, 'admin', NULL, 'organization_teaching_resources', '教学资源', 'catalog', NULL, NULL,
     NULL, 'Collection', 70, 1, 'active',
     '教学资源目录（校区与学期 / 教室管理 / 节次与时间模板）；目录不挂权限码，入口显隐由子页面节点决定', 0);

-- 2) 原两张顶层页面进目录并改名：20 不再含教室与时间段，21 含节次清单
UPDATE sys_menu
SET parent_id = 130,
    menu_name = '校区与学期',
    icon = 'OfficeBuilding',
    sort_no = 10,
    remark = '校区列表、学期列表与校区默认账号；勾选即授组织查看码'
WHERE id = 20 AND menu_code = 'organization_campus';

UPDATE sys_menu
SET parent_id = 130,
    menu_name = '节次与时间模板',
    icon = 'Timer',
    sort_no = 30,
    remark = '时段模板网格编辑与节次清单；勾选即授组织查看码'
WHERE id = 21 AND menu_code = 'organization_time_slots';

-- 3) 新页面：教室管理（校区级）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (131, 'admin', 130, 'organization_classrooms', '教室管理', 'menu', '/classrooms',
     'organization/ClassroomManagementView',
     'organization:read', 'House', 20, 1, 'active',
     '按校区维护教室档案；勾选即授组织查看码', 0);

-- 4) 组织档案维护按钮的覆盖范围说明改一下（码没变，还是那一条 organization:write）
UPDATE sys_menu
SET remark = '校区与学期/教室管理/节次与时间模板三张页面的写操作共用这一个码，勾了即可写'
WHERE id = 116 AND menu_code = 'organization_master_data_write';

-- 5) 目录授权：所有在职角色都补 130，缺了它下面三张页面的入口整块不显示（见头部说明）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1300000 + r.id, r.id, 130, 0
FROM sys_role r
WHERE r.deleted = 0;

-- 6) 教室管理页授权：跟着「原本能看到校区页或时间段页」的角色走（同一族组织档案页面）
--    DISTINCT 是必要的：同时持有 20 与 21 的角色会出两行，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1310000 + rm.role_id, rm.role_id, 131, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id IN (20, 21);
