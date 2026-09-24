-- V55：用户管理列表加「批量导入账号」工具栏按钮
--
-- 与底座 cornerstone/migration 的 V104 内容一字不差，两边都要写：
-- 教培这条库的 sys_list_config 行是本项目 V22 种的（app 只挂 classpath:db/migration，
-- 底座 V100~V199 那批基线脚本不在本项目的执行清单里），只加 V104 的话教培页面上不会出现按钮；
-- 反过来新下游项目跑的是底座基线，只有 V104 才拿得到这个按钮。
-- 两条脚本都是整值覆盖的普通 UPDATE，重复执行结果一致。
--
-- action 值必须与 UserManagementView 里 extraActions 的键一字不差。

UPDATE `sys_list_config`
SET `actions_json` =
'[{"action":"user-create","label":"新增用户","position":"toolbar","buttonType":"primary","permission":"system:user:manage"},{"action":"user-import","label":"批量导入账号","position":"toolbar","permission":"system:user:manage"},{"action":"user-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:user:manage"},{"action":"user-toggle-status","label":"停用","position":"row","permission":"system:user:manage"},{"action":"user-reset-password","label":"重置密码","position":"row","permission":"system:user:manage"},{"action":"user-assign-roles","label":"分配角色","position":"row","permission":"system:user:manage"}]'
WHERE `resource_code` = 'system-users' AND `deleted` = 0;
