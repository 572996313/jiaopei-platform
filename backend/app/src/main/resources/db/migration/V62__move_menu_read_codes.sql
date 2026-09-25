-- V62: 权限码按节点性质归位——读取码上页面节点，与页面同码的按钮废弃
-- 口径（同批写入 AGENTS.md「权限码与菜单节点的分配规则」）：
--   1) 页面(menu)节点挂"进入本页所需的那个码"：业务页挂本模块读码，系统管理页挂它自己的单码(manage)；
--   2) 按钮(button)节点只挂与页面不同的写/动作码；
--   3) 目录(catalog)节点不挂码；接口只需登录的页面也不挂码（纯导航）；
--   4) 同一个码在同一页面只挂一处，不再"页面 + 同名按钮"各挂一份。
-- 改前的问题：系统管理的码全压在按钮节点上、页面节点不带码，「分配菜单」里只勾页面不勾按钮的角色
--   看得见入口、点进去每个接口 403；103「菜单维护」与 109「功能权限查看」还挂着同一个
--   system:menu:manage，勾哪个效果相同，属重复承载。
-- 影响面：这批按钮此前只有平台管理员(id=1)持有，且该角色同时勾了页面与按钮，码上移后授予结果不变。
-- 双胞胎脚本：底座侧 backend/cornerstone-core/src/main/resources/cornerstone/migration/V105__move_menu_read_codes.sql。
--   core 自带的 V1xx 只服务"下游从零建库"，教培自己的库是从 app 的 V1..V6x 长起来的、一条 V1xx 都不跑，
--   所以同一批改动两边各写一份且内容一字不差。

-- 1) 读与写共用单码(manage)的系统管理页：把码挂到页面节点，勾页面即可用
UPDATE sys_menu SET permission_code = 'system:user:manage', remark = '用户管理页；勾选即授该码(该页读与写共用一个码)'
WHERE id = 2 AND menu_code = 'system_users';

UPDATE sys_menu SET permission_code = 'system:role:manage', remark = '角色管理页；勾选即授该码(该页读与写共用一个码)'
WHERE id = 3 AND menu_code = 'system_roles';

UPDATE sys_menu SET permission_code = 'system:menu:manage', remark = '菜单管理页；勾选即授该码(该页读与写共用一个码)'
WHERE id = 4 AND menu_code = 'system_menus';

UPDATE sys_menu SET permission_code = 'system:dict:manage', remark = '字典管理页；勾选即授该码(该页读与写共用一个码)'
WHERE id = 5 AND menu_code = 'system_dicts';

UPDATE sys_menu SET permission_code = 'system:list-config:manage', remark = '列表配置页；勾选即授该码(该页读与写共用一个码)'
WHERE id = 7 AND menu_code = 'system_list_configs';

UPDATE sys_menu SET permission_code = 'system:data-source:manage', remark = '数据源配置页；勾选即授该码(该页读与写共用一个码)'
WHERE id = 11 AND menu_code = 'system_data_sources';

UPDATE sys_menu SET permission_code = 'system:invitation:manage', remark = '注册凭证页；勾选即授该码(该页读与写共用一个码)'
WHERE id = 18 AND menu_code = 'system_invitations';

-- 2) 读/写成对的页面：节点挂读码，写码留在各自的按钮节点上
UPDATE sys_menu SET permission_code = 'system:config:read', remark = '系统参数页；勾选即授参数查看码，改值仍由「参数维护」按钮授予'
WHERE id = 6 AND menu_code = 'system_config';

UPDATE sys_menu SET permission_code = 'tenant:read', remark = '机构资料页；勾选即授机构查看码，编辑仍由「机构资料维护」按钮授予'
WHERE id = 8 AND menu_code = 'system_institution';

UPDATE sys_menu SET permission_code = 'system:operlog:read', remark = '操作日志页；勾选即授日志查看码(该页只读，无写码)'
WHERE id = 10 AND menu_code = 'system_oper_log';

-- 3) 功能权限总览(9)保持不挂码：它的两个接口仅需登录，
--    挂 system:menu:manage 等于"勾了这个页面就白送菜单管理权"，那是越权而不是授权。

-- 4) 与页面同码的按钮废弃（105/108/110 的读码、101/102/103/104/107/111/115 的单码、109 的重复码）
--    保留 106「参数维护」(system:config:write) 与 114「机构资料维护」(tenant:write)：它们是与页面不同的写码。
UPDATE sys_menu SET deleted = 1, remark = '权限码已并入所属页面节点，本按钮废弃'
WHERE deleted = 0 AND id IN (101, 102, 103, 104, 105, 107, 108, 109, 110, 111, 115);

-- 5) 清掉废弃按钮的历史授权，避免「分配菜单」树里留下勾不到的残留
DELETE FROM sys_role_menu WHERE menu_id IN (101, 102, 103, 104, 105, 107, 108, 109, 110, 111, 115);
