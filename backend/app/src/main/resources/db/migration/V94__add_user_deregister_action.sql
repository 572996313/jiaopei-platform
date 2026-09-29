-- V94：用户管理列表加「注销」行内按钮
--
-- 与底座 cornerstone/migration 的 V112 内容一字不差，两边都要写（口径见 V55 头注释）：
-- 教培这条库的 sys_list_config 行是本项目种的，只加 V112 的话本仓库页面上不会出现按钮；
-- 反过来新下游项目跑底座基线，只有 V112 才拿得到这个按钮。
--
-- action 值必须与 UserManagementView 里 extraActions 的键一字不差。
-- 权限沿用 system:user:manage（菜单节点 101「用户管理维护」已带该码），不新增权限码与菜单节点。

UPDATE `sys_list_config`
SET `actions_json` =
'[{"action":"user-create","label":"新增用户","position":"toolbar","buttonType":"primary","permission":"system:user:manage"},{"action":"user-import","label":"批量导入账号","position":"toolbar","permission":"system:user:manage"},{"action":"user-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:user:manage"},{"action":"user-toggle-status","label":"停用","position":"row","permission":"system:user:manage"},{"action":"user-reset-password","label":"重置密码","position":"row","permission":"system:user:manage"},{"action":"user-assign-roles","label":"分配角色","position":"row","permission":"system:user:manage"},{"action":"user-deregister","label":"注销","position":"row","buttonType":"danger","permission":"system:user:manage"}]'
WHERE `resource_code` = 'system-users' AND `deleted` = 0;
