-- V86: 校区页里的「校区默认账号」卡片独立成页，纵向预算压回一屏
--
-- 背景：V85 搬走课程三张卡之后，/campus 实测仍是 1156px（预算 729）。拆开看这三张卡：
--   校区列表 568 / 学期列表 568 / 校区默认账号 520（被 core 的 .resource-grid > :last-child
--   强制独占整行），而三张卡的表体全是 core 那条 `.resource-grid .el-table{min-height:398px}`
--   十行保底钉出来的高度——学期只有 3 行、默认账号当前 0 行，一屏里约 500px 是空白。
-- 这次按"一件事一页"搬走卡片（与 V82 拆节次明细、V83 拆班级名册、V84 拆学员报名、V85 拆课程档案同一套做法）：
--   /campus           校区与学期（两张同类档案卡并排，表体由 TableFillBox 吃满）
--   /campus-accounts  校区默认账号（按校区维护"哪些账号默认落在本校区"，A 型单主表）
-- 拆出去的另一条理由是权限：卡片里的"选择账号"下拉取的是底座用户分页接口，要 system:user:manage，
--   而本模块的读码 organization:read 不需要它——教务角色在原页上天生只能看不能动，同屏没有意义。
-- 顺带删掉页顶「统计」开关展开的两张数量卡：只是复述下面两张表已有的条数（同 V84 删学员页统计条）。
-- 后端接口一个字没改。
--
-- id 避让：菜单 136（现有号段 1~25、101~135、200~215 已占用，按 SELECT id>=136 AND id<200 核对为空）；
--   sys_role_menu 用 1360000 + 角色 ID 取号，与 V81~V85 的 1300000~1350000 同一档。
--
-- 挂码口径（见 AGENTS「权限码与菜单节点的分配规则」）：
--   1) 新页面节点 136 挂 organization:read——读码挂页面节点，勾页面 = 入口可见 + 能读。
--   2) 写码不新增：organization:write（按钮 116）本来就覆盖本模块四张页面，继续共用，只把 remark
--      补上这一页。新页里"选账号"那一步另外要 system:user:manage（用户管理页的码，本迁移不涉及）。
--   3) 节点 136 排在目录 130「教学资源」下、节点 20 之后（sort 50），与校区页同组。

-- 1) 新页面：校区默认账号
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (136, 'admin', 130, 'organization_campus_accounts', '校区默认账号', 'menu', '/campus-accounts',
     'organization/CampusAccountManagementView',
     'organization:read', 'User', 50, 1, 'active',
     '按校区维护账号的默认校区；勾选即授组织查看码', 0);

-- 2) 校区页收窄：默认账号卡片已经搬走
UPDATE sys_menu
SET remark = '校区列表与学期列表；账号的默认校区在「校区默认账号」，课程类档案在「课程档案」；勾选即授组织查看码'
WHERE id = 20 AND menu_code = 'organization_campus';

-- 3) 写码按钮覆盖面补上这一页（仍只有一条 organization:write，不按页拆细）
UPDATE sys_menu
SET remark = '校区与学期/教室管理/时段模板/节次明细/校区默认账号五张页面的写操作共用这一个码，勾了即可写'
WHERE id = 116 AND menu_code = 'organization_master_data_write';

-- 4) 授权：跟着"原本能在校区页看到这张卡片"的角色走（角色持有节点 20 即曾看到它）
--    DISTINCT 是必要的：一个角色可能有多行指向 20，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1360000 + rm.role_id, rm.role_id, 136, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id = 20;
