-- V85: 校区页里的三张教务卡片独立成页「课程档案」，菜单节点跟着搬家
--
-- 背景：/campus 原本六张同类卡竖排（校区/学期/默认账号 + 课程分类/课程/班型），实测纵向 3606px、
--   约五屏，翻页才够得着「新增课程」（客户现场有人的鼠标滚轮是坏的）。课程三张卡与校区没有半点关系：
--   课程、课程分类、班型都是机构级档案，不挂校区，放在校区页只是历史遗留（阶段三拆包时用页面插槽
--   registerPageSlot('campus-detail') 注入，好让组织模块不反向依赖教务模块）。
-- 这次按"一件事一页"搬走（与 V82 拆节次明细、V83 拆班级名册、V84 拆学员报名同一套做法）：
--   /campus   校区与学期（只剩校区列表 + 学期列表 + 校区默认账号）
--   /courses  课程档案（课程为主表，分类与班型两张小卡封顶并排）
-- 后端接口一个字没改，只是取数入口从插槽挪到独立页面；页面插槽扩展点本身留在底座，本仓库已无使用方。
--
-- id 避让：菜单 135（现有号段 1~25、101~129、130~134、200~215 已占用，按 SELECT id>=135 AND id<200 核对为空）；
--   sys_role_menu 用 1350000 + 角色 ID 取号，与 V81~V84 的 1300000~1340000 同一档。
--
-- 挂码口径（见 AGENTS「权限码与菜单节点的分配规则」）：
--   1) 新页面节点 135 自带 education:read——读码挂页面节点，勾页面 = 入口可见 + 能读，
--      与其他教务页共用同一个粗读码是既定形态。
--   2) 写码不新增：education:catalog:write（按钮 123）继续一条覆盖三张表，只把它从节点 20 下面
--      挪到 135 下面，让"勾了课程档案页 + 这条按钮 = 能维护课程/分类/班型"在菜单树上一眼看得清。
--   3) 节点 20 的 remark 不再提"教务主数据区"，避免误导：卡片已经不在这页了。

-- 1) 新页面：课程档案（顶层菜单，与学员/班级/教师档案同级；导航由 module-education 登记）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (135, 'admin', NULL, 'education_courses', '课程档案', 'menu', '/courses',
     'education/CourseManagementView',
     'education:read', 'Files', 45, 1, 'active',
     '课程、课程分类与班型三张档案；勾选即授教务查看码', 0);

-- 2) 写码按钮搬家：123 原来挂在校区页节点下（卡片当年就长在校区页），现在挂到课程档案页
UPDATE sys_menu
SET parent_id = 135,
    remark = '课程/课程分类/班型共用此码，三张表都在「课程档案」页维护，勾了即可写'
WHERE id = 123 AND menu_code = 'education_catalog_write';

-- 3) 校区页收窄：三张教务卡片已经搬走
UPDATE sys_menu
SET remark = '校区列表、学期列表与校区默认账号；课程类档案在「课程档案」；勾选即授组织查看码'
WHERE id = 20 AND menu_code = 'organization_campus';

-- 4) 授权：跟着"原本能在校区页看到这组卡片"的角色走（角色持有节点 20 即曾看到这三张卡）
--    DISTINCT 是必要的：一个角色可能有多行指向 20，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1350000 + rm.role_id, rm.role_id, 135, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id = 20;
