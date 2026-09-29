-- V112：用户管理列表加「注销」行内按钮
--
-- 归属：cornerstone-core（V100~V199 号段），只动底座自己种下的 sys_list_config 行。
--
-- 与教培 app/db/migration 的 V94 内容一字不差，两边都要写：底座基线脚本只在下游从零建库时执行，
-- 教培自己的库是从 app 的 V1..Vn 长起来的，这条 V112 在教培一条都不执行。
-- 已发过下游的 V104 不许原地改（checksum 一变下游直接拒绝启动），要加按钮就新增一条。
--
-- action 值必须与 UserManagementView 里 extraActions 的键一字不差。
-- 权限沿用 system:user:manage，不新增权限码与菜单节点。

UPDATE `sys_list_config`
SET `actions_json` =
'[{"action":"user-create","label":"新增用户","position":"toolbar","buttonType":"primary","permission":"system:user:manage"},{"action":"user-import","label":"批量导入账号","position":"toolbar","permission":"system:user:manage"},{"action":"user-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:user:manage"},{"action":"user-toggle-status","label":"停用","position":"row","permission":"system:user:manage"},{"action":"user-reset-password","label":"重置密码","position":"row","permission":"system:user:manage"},{"action":"user-assign-roles","label":"分配角色","position":"row","permission":"system:user:manage"},{"action":"user-deregister","label":"注销","position":"row","buttonType":"danger","permission":"system:user:manage"}]'
WHERE `resource_code` = 'system-users' AND `deleted` = 0;
