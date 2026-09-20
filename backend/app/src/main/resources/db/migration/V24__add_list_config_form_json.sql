-- V24: sys_list_config 增加 form_json(新增/编辑弹窗表单配置)
-- 三个内置资源(system-users / system-roles / system-configs)补初始表单配置,
-- 与改造前手写弹窗的字段保持一致(仅新增/仅编辑、禁用规则、校验提示均已在 JSON 内表达),
-- 上线后弹窗视觉与行为零变化。

ALTER TABLE sys_list_config
    ADD COLUMN form_json TEXT NULL COMMENT '弹窗表单配置 JSON(fields 数组),为空表示该资源未配置表单'
    AFTER actions_json;

-- 用户管理:新增/编辑用户弹窗
-- 机构→校区联动(handleInstitutionChange)为代码钩子,由宿主页面注册
UPDATE sys_list_config SET form_json = '{
  "labelWidth": "auto",
  "gridColumns": 2,
  "fields": [
    { "field": "institutionId", "label": "机构", "type": "select", "required": true, "gridSpan": 1,
      "optionsLoader": "institutions", "onChange": "handleUserInstitutionChange" },
    { "field": "defaultCampusId", "label": "默认校区", "type": "select", "gridSpan": 1,
      "clearable": true, "optionsLoader": "campuses" },
    { "field": "username", "label": "账号", "type": "input", "required": true, "gridSpan": 1,
      "disabledOn": ["edit"], "placeholder": "登录账号" },
    { "field": "displayName", "label": "显示名称", "type": "input", "required": true, "gridSpan": 1,
      "placeholder": "姓名/昵称" },
    { "field": "phone", "label": "手机号", "type": "input", "gridSpan": 1, "maxlength": 32 },
    { "field": "status", "label": "状态", "type": "select", "gridSpan": 1,
      "optionsLoader": "status", "defaultValue": "active" },
    { "field": "initialPassword", "label": "初始化密码", "type": "password", "gridSpan": 1,
      "showOn": ["create"], "placeholder": "留空则使用默认初始密码", "maxlength": 128 },
    { "field": "remark", "label": "备注", "type": "textarea", "gridSpan": 2, "maxlength": 500 }
  ]
}' WHERE resource_code = 'system-users' AND deleted = 0;

-- 角色管理:新增/编辑角色弹窗
UPDATE sys_list_config SET form_json = '{
  "labelWidth": "auto",
  "gridColumns": 2,
  "fields": [
    { "field": "roleCode", "label": "角色编码", "type": "input", "required": true, "gridSpan": 1,
      "disabledOn": ["edit"], "placeholder": "如 CAMPUS_ADMIN" },
    { "field": "roleName", "label": "角色名称", "type": "input", "required": true, "gridSpan": 1,
      "placeholder": "如 校区管理员" },
    { "field": "status", "label": "状态", "type": "select", "gridSpan": 1,
      "optionsLoader": "status", "defaultValue": "active" },
    { "field": "permissionCodes", "label": "预设权限", "type": "permission-groups", "gridSpan": 2 },
    { "field": "customPermissionCodes", "label": "补充权限码", "type": "textarea", "gridSpan": 2,
      "placeholder": "逗号或换行分隔未知权限码", "rows": 2 },
    { "field": "remark", "label": "备注", "type": "textarea", "gridSpan": 2, "maxlength": 500 }
  ]
}' WHERE resource_code = 'system-roles' AND deleted = 0;

-- 系统参数:新增/编辑系统参数弹窗
UPDATE sys_list_config SET form_json = '{
  "labelWidth": "auto",
  "gridColumns": 2,
  "fields": [
    { "field": "configKey", "label": "配置键", "type": "input", "required": true, "gridSpan": 1,
      "disabledOn": ["edit"], "placeholder": "如 app.name", "maxlength": 128 },
    { "field": "displayName", "label": "显示名称", "type": "input", "required": true, "gridSpan": 1,
      "maxlength": 64 },
    { "field": "configValue", "label": "配置值", "type": "input", "required": true, "gridSpan": 1,
      "placeholder": "当前生效值" },
    { "field": "valueType", "label": "值类型", "type": "select", "required": true, "gridSpan": 1,
      "disabledOn": ["edit"], "options": [
        { "label": "字符串", "value": "string" },
        { "label": "整数", "value": "int" },
        { "label": "布尔", "value": "boolean" },
        { "label": "JSON", "value": "json" }
      ], "defaultValue": "string" },
    { "field": "configGroup", "label": "配置分组", "type": "input", "required": true, "gridSpan": 1,
      "disabledOn": ["edit"], "placeholder": "如 general/auth/system/business" },
    { "field": "sortOrder", "label": "排序", "type": "number", "gridSpan": 1, "min": 0, "max": 9999,
      "defaultValue": 0 },
    { "field": "isSystem", "label": "系统内置", "type": "switch", "gridSpan": 1, "showOn": ["create"],
      "defaultValue": false },
    { "field": "status", "label": "状态", "type": "select", "gridSpan": 1,
      "optionsLoader": "status", "defaultValue": "active" },
    { "field": "defaultValue", "label": "默认值", "type": "input", "gridSpan": 2 },
    { "field": "description", "label": "配置说明", "type": "textarea", "gridSpan": 2, "maxlength": 500 }
  ]
}' WHERE resource_code = 'system-configs' AND deleted = 0;
