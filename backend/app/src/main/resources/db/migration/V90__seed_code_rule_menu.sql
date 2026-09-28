-- ============================================================================
-- V90：给「编号规则」维护页种下菜单节点与授权
--
-- 与 cornerstone-core jar 内的 cornerstone/migration/V109__seed_code_rule_menu.sql
-- 是同一批改动的两份落点，两条 INSERT 一字不差（AGENTS「模块装配与开关」的双轨规则）：
-- 教培自己的库从 V1..V89 长起来，cornerstone/migration 那条目录不在本仓库的
-- cornerstone.flyway.locations 里，所以 V109 在教培一条都不会执行；反过来新项目
-- 只跑 V1xx，拿不到本文件。少写任何一份，另一条路径就缺这个入口。
--
-- 为什么这次要连菜单一起种：sys_code_rule 与取号接口在 V88 已经就位，但接口读写都挂
--   system:code-rule:manage，没有菜单节点承载这个码，除了 CSV 里手填没人能进这一页；
--   而资源清单是代码登记的（各模块 CodeRuleContributor），页面上看不见也改不了。
--
-- 挂码口径（AGENTS「权限码与菜单节点的分配规则」）：
--   这一页的读与写共用单码（GET 列表与 PUT 保存都拦 system:code-rule:manage），
--   按第 4 条把码挂在**页面节点**上、不再配同名"维护"按钮——页面与按钮挂同一个码
--   等于纯重复，还会让人误以为勾了页面不够。与 V62 收口后的用户/角色/字典/
--   数据源/注册凭证同一形态。
--
-- 落点选择：父节点 13「低代码配置」（字典 5 / 数据源 11 / 列表配置 7 都在这族里），
--   sort_no 40 排在列表配置之后。编号规则管的正是"编码这一列怎么长出来"，与那三张
--   元数据配置表同族，不放「系统运维」（那是参数与留痕）。
--
-- 授权范围：只有平台管理员(id=1)。库里现在持有 5/7/11/13 的角色也就只有 1，
--   机构运营(110)与教师(111)按 AGENTS 第 8 条一律不给系统设置页——底座的
--   用户/角色/机构三条链路还不比机构范围，勾上即跨机构读写。
--   不给 sys_role.permission_codes 补 CSV：菜单节点已带这个码，取权限时两条路取并集，
--   再写一份就是 AGENTS 说的"两份账"，admin 的 CSV 收敛是另批。
--
-- id 避让：菜单 1~25、101~123、130~137 已占用，本次取 138（SELECT id=138 核对为空）；
--   sys_role_menu 用 1380000 + 角色 ID 取号，与 V81~V87 的 1300000~1370000 同一档。
--
-- GET /api/system/code-rules/next 不在这次范围内、也不需要节点：那颗"生成"按钮刻意只要
--   登录不要权限码（教师也要能给自己填编码），所以它不进权限总览、不给任何节点。
-- ============================================================================

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (138, 'admin', 13, 'system_code_rules', '编号规则', 'menu', '/system/code-rules',
     'system/code-rules',
     'system:code-rule:manage', 'Ticket', 40, 1, 'active',
     '实体编号的前缀、日期段与序号位数；勾选即授该码(该页读与写共用一个码)，某资源未启用时它的新增页留空仍报"编号不能为空"', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES (1380001, 1, 138, 0);
