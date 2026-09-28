-- V87: 把「班型」从课程档案页的辅助卡拆成独立页，课程页只剩课程与它的分类
--
-- 背景：V85 把校区页的三张教务卡片一起搬成「课程档案」页（课程 + 课程分类 + 班型），实测 1440×900
--   整页 729px 里两张辅助卡并排占 229 + 间距 18 = 247（首屏 34%），只换回各三行——分类 7 条藏 4 条、
--   班型 9 条藏 6 条，课程主表表体只剩 279px（每页 10 条只能看见 6 行）。客户现场有人的鼠标滚轮是坏的，
--   "三个模块都露一小截、谁都看不全"是这一页被反馈的问题。
-- 拆的依据不是"卡片太多"，而是**这三张表里只有分类与课程有关系**：分类是课程表的一列、也是新增课程
--   弹窗里的下拉；班型与课程零交集——课程表没有班型列、课程弹窗没有班型字段，它的消费方是
--   「班级档案」建班时选的 classTypeId 与「班级名册」的回显。它留在课程页只是 V85 一起搬过来的历史结果。
--   （人数上限也不在班型上：班级的超容判断读的是 jp_class.capacity_limit。）
-- 这次按"一件事一页"搬走（与 V82 拆节次明细、V83 拆班级名册、V84 拆学员报名、V85 拆课程档案、
--   V86 拆校区默认账号同一套做法）：
--   /courses      课程档案（课程主表 + 课程分类卡；分类卡整行铺开，补显示备注列）
--   /class-types  班型档案（A 型单主表：编码/名称/授课方式/人数区间/状态/备注全列出）
-- 后端接口一个字没改。班型原来是全量小表、没有分页接口，新页同样一次性全量加载 + TableFillBox 吃满，
--   所以班型页不再受"三行封顶"限制，9 条一屏看全。
--
-- id 避让：菜单 137（现有号段 1~25、101~136、200~215 已占用，按 SELECT id>=137 AND id<200 核对为空）；
--   sys_role_menu 用 1370000 + 角色 ID 取号，与 V81~V86 的 1300000~1360000 同一档。
--
-- 挂码口径（见 AGENTS「权限码与菜单节点的分配规则」）：
--   1) 新页面节点 137 挂 education:read——读码挂页面节点，勾页面 = 入口可见 + 能读，
--      与课程档案等教务页共用同一个粗读码是既定形态。
--   2) 写码不新增：education:catalog:write（按钮 123）继续一条覆盖课程/课程分类/班型三张表，
--      节点仍挂在 135 下（一个码只挂一处），班型页的 remark 说清"要改班型得勾课程档案页下那条按钮"。
--      两页互跳，只勾 137 的人能看不能改，不是 403 空洞。
--   3) 节点 135 的 remark 不再提班型。

-- 1) 新页面：班型档案（顶层菜单，排在「课程档案」之后；导航由 module-education 登记）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (137, 'admin', NULL, 'education_class_types', '班型档案', 'menu', '/class-types',
     'education/ClassTypeManagementView',
     'education:read', 'Grid', 46, 1, 'active',
     '班型编码、名称、授课方式与人数区间；建班级时在「班级档案」选择，要改动班型需再勾「课程档案」下的课程主数据维护；勾选即授教务查看码', 0);

-- 2) 课程页收窄：班型卡片已经搬走
UPDATE sys_menu
SET remark = '课程与课程分类；班型在「班型档案」，班级怎么搭在「班级档案」；勾选即授教务查看码'
WHERE id = 135 AND menu_code = 'education_courses';

-- 3) 写码按钮的覆盖面说明（码没变，还是那一条）
UPDATE sys_menu
SET remark = '课程/课程分类/班型共用此码，「课程档案」与「班型档案」两页的写操作都在这一条里，勾了即可写'
WHERE id = 123 AND menu_code = 'education_catalog_write';

-- 4) 授权：跟着"原本能看到课程档案页（班型卡片就在里面）"的角色走（两页互跳，缺一个就是死链）
--    DISTINCT 是必要的：一个角色可能有多行指向 135，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1370000 + rm.role_id, rm.role_id, 137, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id = 135;
