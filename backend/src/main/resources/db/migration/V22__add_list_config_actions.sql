-- V22: Add actions_json to sys_list_config for configurable buttons
-- 按钮配置 JSON 数组:action(代码侧动作名)/label/position(toolbar|row)/buttonType/permission。
-- actions_json IS NULL = 未配置按钮 → 前端回退 #actions 插槽模式;[] = 明确无按钮。
-- 三条种子配置写入与改造前一致的按钮,保证行为零变化。

ALTER TABLE sys_list_config
    ADD COLUMN actions_json TEXT NULL COMMENT '按钮配置 JSON 数组,为空时前端回退插槽模式' AFTER query_json;

-- 用户管理:新增(工具栏),行内 编辑/停用|启用(动态文案由代码动作提供)/重置密码/分配角色
UPDATE sys_list_config SET actions_json =
'[{"action":"user-create","label":"新增用户","position":"toolbar","buttonType":"primary","permission":"system:user:manage"},{"action":"user-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:user:manage"},{"action":"user-toggle-status","label":"停用","position":"row","permission":"system:user:manage"},{"action":"user-reset-password","label":"重置密码","position":"row","permission":"system:user:manage"},{"action":"user-assign-roles","label":"分配角色","position":"row","permission":"system:user:manage"}]'
WHERE resource_code = 'system-users' AND deleted = 0;

-- 角色管理:新增(工具栏),行内 编辑/分配菜单(需菜单权限)/停用|启用
UPDATE sys_list_config SET actions_json =
'[{"action":"role-create","label":"新增角色","position":"toolbar","buttonType":"primary","permission":"system:role:manage"},{"action":"role-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:role:manage"},{"action":"role-assign-menus","label":"分配菜单","position":"row","permission":"system:menu:manage"},{"action":"role-toggle-status","label":"停用","position":"row","permission":"system:role:manage"}]'
WHERE resource_code = 'system-roles' AND deleted = 0;

-- 系统参数:新增(工具栏),行内 编辑/删除(系统内置禁删,由代码动作 disabled 控制)
UPDATE sys_list_config SET actions_json =
'[{"action":"config-create","label":"新增参数","position":"toolbar","buttonType":"primary","permission":"system:config:write"},{"action":"config-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:config:write"},{"action":"config-delete","label":"删除","position":"row","buttonType":"danger","permission":"system:config:write"}]'
WHERE resource_code = 'system-configs' AND deleted = 0;
