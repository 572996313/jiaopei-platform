-- V74: 教务写码按落点拆分，并种入两个可直接使用的预设角色
--
-- 背景：education:write 一个码压在 17 个控制器、48 个写接口上（点名、排课、班级、学员、档案、课程主数据全共用），
-- 导致"教师"这个角色配不出来——不给写码只能看，给了写码等于全校教务超级权限。本脚本把它拆成六枚：
--   education:attendance:write   课次点名（服务层再限"仅本人授课课次"，见 AttendanceServiceImpl）
--   education:attendance:manage  考勤管理（任意课次代点 + 考勤记录新增）
--   education:schedule:write     排课维护（计划/规则/课次增删改/冲突检查/校区日历停排日）
--   education:class:write        班级与学员维护（班级/任课/入班/报名/邀请码小程序码）
--   education:profile:write      档案维护（教师/学员/家长档案、教师导入与登录账号绑定）
--   education:catalog:write      课程主数据维护（课程/课程分类/班型，卡片挂在校区页）
-- education:read 本批不拆（教师预设持它可读全校学员/教师/班级列表，要收这个口得另批拆读码）。
--
-- 三个口径要记住：
--   1) 读码挂页面、写码挂 visible=0 按钮（AGENTS「权限码与菜单节点的分配规则」）；一个码只挂一处，
--      跨页面的覆盖范围写在 remark 里（与 V61 的 116「组织档案维护」同构）。
--   2) 113「执行变更」原语义含"结课/调课/停课/补课"，那些接口归 schedule:write，所以本脚本把 112
--      留给排课维护、113 收窄成课次点名。只在「分配菜单」里勾过 112/113、CSV 里没有 education:write
--      的角色拿不到新码——上线前在**旧库**跑一次下面这条核查，命中就手工补勾：
--        SELECT r.id, r.role_code, r.role_name FROM sys_role r
--        JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0 AND rm.menu_id IN (112, 113)
--        WHERE r.deleted = 0 AND (r.permission_codes = '' OR r.permission_codes NOT LIKE '%education:write%');
--   3) 预设角色的权限一律由菜单节点承载，permission_codes 留空：生效权限是「CSV ∪ 已勾节点」的并集，
--      两边都写会出现"撤了菜单接口还通"，留空则调整只有一处（运维在角色管理的「分配菜单」里改）。
--      侧边栏入口只看 sys_role_menu、不看 CSV，所以预设角色必须勾节点，只写 CSV 会登进去一片空白。
--
-- id 避让：菜单 120~123 接续 119；角色 110/111 避开 1 与小程序的 101/102；sys_role_menu 用 7000+ 段。

-- 1) 原两枚写按钮改挂拆分后的码
UPDATE sys_menu
SET permission_code = 'education:schedule:write',
    remark = '排课计划/规则/课次增删改与冲突检查、校区日历停排日共用此码；执行页的结课/调课/停课/补课也在内'
WHERE id = 112 AND menu_code = 'education_scheduling_write';

UPDATE sys_menu
SET permission_code = 'education:attendance:write',
    menu_name = '课次点名',
    remark = '保存课次点名；服务层限本人授课课次，要代点任意课次需另授「考勤管理」'
WHERE id = 113 AND menu_code = 'education_execution_write';

-- 2) 拆分出来的另四枚写码，各自一个隐藏按钮节点
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (120, 'admin', 17, 'education_attendance_manage', '考勤管理', 'button', NULL, NULL,
     'education:attendance:manage', NULL, 20, 0, 'active',
     '代点任意课次与新增考勤记录；机构管理员与教务岗持有，教师岗不给', 0),
    (121, 'admin', 23, 'education_class_write', '班级与学员维护', 'button', NULL, NULL,
     'education:class:write', NULL, 10, 0, 'active',
     '班级/任课教师/入班/报名/邀请码小程序码共用此码，勾了即可写这几页的操作', 0),
    (122, 'admin', 24, 'education_profile_write', '档案维护', 'button', NULL, NULL,
     'education:profile:write', NULL, 10, 0, 'active',
     '教师与学员档案、家长联系人、教师批量导入与登录账号绑定共用此码，勾了即可写', 0),
    (123, 'admin', 20, 'education_catalog_write', '课程主数据维护', 'button', NULL, NULL,
     'education:catalog:write', NULL, 20, 0, 'active',
     '课程/课程分类/班型共用此码（三张卡片在校区页的教务主数据区维护）', 0);

-- 3) 所有原持 education:write 的角色换成六枚新码，保持授予结果等价（含平台管理员与机构自建角色）
UPDATE sys_role
SET permission_codes = REPLACE(
    permission_codes,
    'education:write',
    'education:attendance:write,education:attendance:manage,education:schedule:write,education:class:write,education:profile:write,education:catalog:write'
)
WHERE permission_codes LIKE '%education:write%';

-- 4) 平台管理员补勾新增的四枚按钮节点（112/113 它早已持有）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (7001, 1, 120, 0),
    (7002, 1, 121, 0),
    (7003, 1, 122, 0),
    (7004, 1, 123, 0);

-- 5) 两个预设角色：权限全部由下面的菜单节点承载，CSV 留空
INSERT IGNORE INTO sys_role (
    id, role_code, role_name, permission_codes, status, remark, deleted
) VALUES
    (110, 'INSTITUTION_ADMIN', '机构运营', '', 'active',
     '预设角色：机构日常运营用，覆盖教务/组织/公告全部业务页面的查看与维护；不含账号、角色、机构资料、字典、参数、菜单、日志等平台侧配置（底座这些接口不分机构范围，放开就能跨机构读写，待补完校验再放）。权限在「分配菜单」里调整', 0),
    (111, 'TEACHER', '教师', '', 'active',
     '预设角色：只有教务查看码与课次点名码，侧边栏仅「执行与课表」；点名受数据范围限制，只能点本人授课的课次。教师账号须绑定教师档案（jp_teacher.user_id）', 0);

-- 6) 机构管理员的菜单授权：业务目录 + 页面 + 按钮一层不落（缺目录会被 buildTree 提升为根、分组失效）
--    **本批只给业务页面（jp_* 一侧），整段「系统设置」都不给**，原因是底座这三处不做机构范围校验：
--      a) 8「机构资料」(tenant:read) — InstitutionServiceImpl#listInstitutions 返回全库机构；
--      b) 114「机构资料维护」(tenant:write) — 还带 POST /api/organizations/institutions，建机构无平台侧限制；
--      c) 2「用户管理」(system:user:manage) / 3「角色管理」(system:role:manage) —
--         SystemUserServiceImpl#pageUsers 不加机构条件就是全库账号，createUser 只校验"机构存在"、
--         能把账号建进别人机构；SystemRoleServiceImpl#updateRole 按 id 改任意角色（含平台管理员自身）。
--    所以"机构自己管账号/角色"的前置条件是先把 core 的 institution/user/role 三条链路补上
--    AuthContext 机构比对（与 AGENTS「安全与数据一致性约定」第 1 条同口径），属另一批。
--    那批落地后在此补勾 7101(1)/7102(12)/7103(2)/7104(3)/7105(8)/7106(114)，预设角色才算完整的机构管理员。
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (7107, 110, 15, 0),
    (7108, 110, 16, 0),
    (7109, 110, 17, 0),
    (7110, 110, 19, 0),
    (7111, 110, 112, 0),
    (7112, 110, 113, 0),
    (7113, 110, 120, 0),
    (7114, 110, 20, 0),
    (7115, 110, 21, 0),
    (7116, 110, 22, 0),
    (7117, 110, 23, 0),
    (7118, 110, 24, 0),
    (7119, 110, 25, 0),
    (7120, 110, 116, 0),
    (7121, 110, 119, 0),
    (7122, 110, 121, 0),
    (7123, 110, 122, 0),
    (7124, 110, 123, 0);

-- 7) 教师的菜单授权：排课中心目录 + 执行与课表页（带教务查看码）+ 课次点名按钮
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (7201, 111, 15, 0),
    (7202, 111, 17, 0),
    (7203, 111, 113, 0);
