-- V83: 「班级学员」一页拆成两页——档案页只管这个班怎么搭，名册页管班里有哪些人
--
-- 背景：原 /classes 一屏叠了五段（筛选 → 班级主档 + 风险通知 → 班级学员关系 →
--   班级教师 + 家长联系人），实测纵向约 1789px，1440×900 的预算（页签行以下 729px）
--   差一倍多，「新增入班」「新增教师」这些主操作全在第二屏（客户现场有人的鼠标滚轮是坏的）。
-- 按"一件事一页"拆开：
--   /classes        班级档案（主档 + 风险与通知 + 任课教师，编辑/删除班级）
--   /class-roster   班级名册（入班、退班、家长联系人；家长联系人收在行上的抽屉里，不占纵向预算）
-- 后端接口一个字没改，只是取数入口从一页挪到两页。
--
-- id 避让：菜单 133（现有号段 1~25、101~129、130~132、200~215，已按 SELECT id>=133 AND id<200 核对为空）；
--   sys_role_menu 用 1330000 + 角色 ID 取号，与 V81/V82 的 1300000/1310000/1320000 同一档、不与之重叠。
--
-- 挂码口径（见 AGENTS「权限码与菜单节点的分配规则」）：
--   1) 新页面节点 133 自带 education:read——读码挂页面节点，勾页面 = 入口可见 + 能读。
--      粗读码在多个页面节点上重复挂是本项目的既定形态（勾哪个页面决定进哪个页），不是重复配置。
--   2) 不再给名册页配第二个 education:class:write 按钮节点：同一个码配两个节点只是让操作员
--      以为"勾一个不够"。121 那一条继续覆盖班级/任课教师/入班/报名的写操作，只改 remark 说明范围。
--   3) 两页互跳，授权要一起给：只勾 23 不勾 133 的人点「班级名册」会进不去（反之只勾 133
--      的人看不到档案页），所以 133 跟着"原本能看到班级学员页"的角色批量补。

-- 1) 原页面收窄成"只管档案"，名字跟着改（这一页已经没有学员名册了）
UPDATE sys_menu
SET menu_name = '班级档案',
    sort_no = 60,
    remark = '班级主档、风险通知与任课教师；入班、退班和家长联系人在「班级名册」；勾选即授教务查看码'
WHERE id = 23 AND menu_code = 'education_classes';

-- 2) 新页面：班级名册（入班/退班 + 家长联系人抽屉）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (133, 'admin', NULL, 'education_class_roster', '班级名册', 'menu', '/class-roster',
     'education/ClassRosterView',
     'education:read', 'Notebook', 65, 1, 'active',
     '班级学员名册：新增入班、移除、家长联系人；勾选即授教务查看码', 0);

-- 3) 班级与学员维护按钮的覆盖范围说明（码没变，还是那一条 education:class:write）
UPDATE sys_menu
SET remark = '班级/任课教师/入班/报名/邀请码小程序码共用此码，档案页与名册页的写操作都在这一条里，勾了即可写'
WHERE id = 121 AND menu_code = 'education_class_write';

-- 4) 名册页授权：跟着「原本能看到班级页」的角色走（两页互跳，缺一个就是死链）
--    DISTINCT 是必要的：一个角色可能有多行指向 23，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1330000 + rm.role_id, rm.role_id, 133, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id = 23;
