-- V104：用户管理列表加「批量导入账号」工具栏按钮
--
-- 归属：cornerstone-core（V100~V199 号段），只动底座自己种下的 sys_list_config 行。
--
-- 为什么是新增脚本而不是改 V101：V101 已经跑在库上，改一个字就会换 Flyway checksum，
-- 老库下次启动直接 Migration checksum mismatch。
--
-- 为什么走 actions_json 而不是在页面里写死按钮：按钮的权限码与位置由配置决定，
-- 这里只登记动作名，真正的弹窗由 UserManagementView 的 extraActions 注册；
-- 没有注册处理器的动作名，列表只会 console.warn 并且点了没反应，不会出现"看得见按不动"。
--
-- 与前端的关系：action 值必须与 UserManagementView 里 extraActions 的键一字不差。

UPDATE `sys_list_config`
SET `actions_json` =
'[{"action":"user-create","label":"新增用户","position":"toolbar","buttonType":"primary","permission":"system:user:manage"},{"action":"user-import","label":"批量导入账号","position":"toolbar","permission":"system:user:manage"},{"action":"user-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:user:manage"},{"action":"user-toggle-status","label":"停用","position":"row","permission":"system:user:manage"},{"action":"user-reset-password","label":"重置密码","position":"row","permission":"system:user:manage"},{"action":"user-assign-roles","label":"分配角色","position":"row","permission":"system:user:manage"}]'
WHERE `resource_code` = 'system-users' AND `deleted` = 0;
