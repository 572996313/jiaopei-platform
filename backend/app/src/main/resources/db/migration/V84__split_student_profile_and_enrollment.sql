-- V84: 「报名与入班」一页拆成两页——档案页管学员本身，报名页管这孩子报过哪些班
--
-- 背景：原 /students 一屏叠了三张主表（学员档案 + 报名记录 + 家长联系人），每张都套了一层
--   TableFillBox，实测纵向约 1401px，1440×900 的预算（页签行以下 729px）差近一倍，
--   「新增报名」「新增家长联系人」全在第二屏（客户现场有人的鼠标滚轮是坏的）。
-- 三张表同屏不是"它们相关"，而是后端视角：查学员档案不需要同时看报名，打电话时才要联系人。
-- 按"一件事一页"拆开（与家长联系人收进抽屉，与 V83 拆 /classes 同一套做法）：
--   /students     学员档案（新增/编辑/删除学员 + 行上抽屉维护家长联系人）
--   /enrollments  学员报名（报名记录分页 + 新增报名；入班仍去「班级名册」）
-- 后端接口一个字没改，只是取数入口从一页挪到两页。
--
-- id 避让：菜单 134（号段 1~25、101~129、130~133、200~215 已占用，按 SELECT id>=134 AND id<200 核对为空）；
--   sys_role_menu 用 1340000 + 角色 ID 取号，与 V81/V82/V83 的 1300000/1310000/1320000/1330000 同一档。
--
-- 挂码口径（见 AGENTS「权限码与菜单节点的分配规则」）：
--   1) 新页面节点 134 自带 education:read——读码挂页面节点，勾页面 = 入口可见 + 能读。
--      与 22 共用同一个粗读码是既定形态（勾哪个页面决定进哪个页），不是重复配置。
--   2) 不给两页各配一份写码：学员档案的写走 education:profile:write（按钮 122，与教师档案共用），
--      报名的写走 education:class:write（按钮 121，与班级档案/班级名册共用）。同一个码配两个节点
--      只会让操作员以为"勾一个不够"，所以只在这两条按钮的 remark 里补清覆盖面。
--   3) 两页互跳，授权要一起给：只勾 22 的人点「学员档案」进不去（反之只勾 134 的人看不到报名页），
--      所以 134 跟着"原本能看到报名与入班页"的角色批量补。

-- 1) 原页面收窄成"只管报名"：编码 education_enrollments 不动（改码要连前端登记表一起对），
--    名字、路径与组件指向拆出来的报名页
UPDATE sys_menu
SET menu_name = '学员报名',
    path = '/enrollments',
    component = 'education/EnrollmentManagementView',
    icon = 'Ticket',
    sort_no = 55,
    remark = '学员报名记录与新增报名；学员本身的档案在「学员档案」，办理入班在「班级名册」；勾选即授教务查看码'
WHERE id = 22 AND menu_code = 'education_enrollments';

-- 2) 新页面：学员档案（新增/编辑/删除学员 + 行上家长联系人抽屉）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (134, 'admin', NULL, 'education_students', '学员档案', 'menu', '/students',
     'education/StudentManagementView',
     'education:read', 'UserFilled', 50, 1, 'active',
     '学员档案：新增、编辑、删除学员与家长联系人；报名与入班在别的页；勾选即授教务查看码', 0);

-- 3) 两条写码按钮的覆盖范围说明（码没变，还是那两条），把拆页后的落点写清楚
UPDATE sys_menu
SET remark = '教师与学员档案、家长联系人、导入绑号共用此码；「教师档案」「学员档案」两页的写操作都在这一条里，勾了即可写'
WHERE id = 122 AND menu_code = 'education_profile_write';

UPDATE sys_menu
SET remark = '班级/任课教师/入班/报名/邀请码小程序码共用此码，「班级档案」「班级名册」「学员报名」三页的写操作都在这一条里，勾了即可写'
WHERE id = 121 AND menu_code = 'education_class_write';

-- 4) 档案页授权：跟着「原本能看到报名与入班页」的角色走（两页互跳，缺一个就是死链）
--    DISTINCT 是必要的：一个角色可能有多行指向 22，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1340000 + rm.role_id, rm.role_id, 134, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id = 22;
