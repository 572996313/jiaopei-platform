-- V97: 角色弹窗隐藏权限码网格，列表按钮改为「授权页面与按钮」
-- 背景：客户看不懂 system:user:manage 这类权限码，只认页面与按钮。角色弹窗从本批起
--   只呈现「页面 → 操作」两级勾选（数据来自代码侧按钮授权目录 + sys_menu 节点），
--   历史 permission_codes CSV 收进平台管理员的「高级视图」，不再出现在普通表单里。
-- 注意：隐藏字段不等于清空数据。前端 RoleManagementView 保存时必须把编辑前的
--   permissionCodes 原样回传；本脚本只改展示配置，不动 sys_role.permission_codes。
--
-- 双胞胎脚本：底座 cornerstone-core 的 cornerstone/migration/V113__hide_role_permission_grid.sql，
--   两份内容一字不差。本仓库的 V1xx 只服务"下游从零建库"，教培自己的库从 app 的 V1..V9x 长起来。
--
-- 权限口径：「授权页面与按钮」属角色管理职责，列表按钮的权限从 system:menu:manage 改为
--   system:role:manage；后端新接口内置"「系统设置」子树只能由平台管理员授权"的校验，
--   不会因此让机构角色管理员给自己授系统页。

UPDATE sys_list_config
SET `form_json` = '{
  "labelWidth": "auto",
  "gridColumns": 2,
  "fields": [
    { "field": "roleCode", "label": "角色编码", "type": "input", "required": true, "gridSpan": 1,
      "disabledOn": ["edit"], "placeholder": "如 CAMPUS_ADMIN" },
    { "field": "roleName", "label": "角色名称", "type": "input", "required": true, "gridSpan": 1,
      "placeholder": "如 机构管理员" },
    { "field": "status", "label": "状态", "type": "select", "gridSpan": 1,
      "optionsLoader": "status", "defaultValue": "active" },
    { "field": "remark", "label": "备注", "type": "textarea", "gridSpan": 2, "maxlength": 500 }
  ]
}'
WHERE `resource_code` = 'system-roles' AND `deleted` = 0;

UPDATE sys_list_config
SET `actions_json` = '[{"action":"role-create","label":"新增角色","position":"toolbar","buttonType":"primary","permission":"system:role:manage"},{"action":"role-edit","label":"编辑","position":"row","buttonType":"primary","permission":"system:role:manage"},{"action":"role-assign-menus","label":"授权页面与按钮","position":"row","permission":"system:role:manage"},{"action":"role-toggle-status","label":"停用","position":"row","permission":"system:role:manage"},{"action":"role-advanced-csv","label":"高级视图","position":"row","permission":"system:menu:manage"}]'
WHERE `resource_code` = 'system-roles' AND `deleted` = 0;
