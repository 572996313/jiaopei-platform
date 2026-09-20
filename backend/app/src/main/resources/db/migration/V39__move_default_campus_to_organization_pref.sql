-- V39: "默认校区"移出底座用户表
-- 原 jp_user_account.default_campus_id 是底座表里的教务业务列，且带一条指向业务表 jp_campus 的
-- 物理外键，导致 cornerstone-core 的建表脚本无法独立于教培业务模块执行（第二个项目只要底座就起不来）。
-- 本迁移把这一列外移到业务模块的组织表 jp_user_campus_pref，底座用户表不再认识校区。
-- 校区归属与账号归属的校验随之移到 UserCampusPrefServiceImpl，底座不再有 CampusBelongingValidator SPI。

CREATE TABLE jp_user_campus_pref (
    id BIGINT NOT NULL COMMENT '主键(雪花ID)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(租户)',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    campus_id BIGINT NULL COMMENT '默认校区ID(NULL=未设置)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_user_campus_pref_user (user_id, deleted),
    KEY idx_jp_user_campus_pref_campus (campus_id),
    KEY idx_jp_user_campus_pref_institution (institution_id),
    CONSTRAINT fk_jp_user_campus_pref_user FOREIGN KEY (user_id) REFERENCES jp_user_account (id),
    CONSTRAINT fk_jp_user_campus_pref_campus FOREIGN KEY (campus_id) REFERENCES jp_campus (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户默认校区偏好(教务业务侧,原底座用户表 default_campus_id 列外移)';

-- 搬运既有取值:偏好行直接复用账号 ID 作为主键,一个账号最多一条偏好。
-- 已逻辑删除的账号一并搬运，避免后续恢复账号时偏好丢失。
INSERT INTO jp_user_campus_pref (id, institution_id, user_id, campus_id, deleted)
SELECT id, institution_id, id, default_campus_id, deleted
FROM jp_user_account
WHERE default_campus_id IS NOT NULL;

-- 先删外键再删列:InnoDB 为外键自动建的索引会在删列时一并移除
ALTER TABLE jp_user_account DROP FOREIGN KEY fk_jp_user_default_campus;
ALTER TABLE jp_user_account DROP COLUMN default_campus_id;

-- 底座用户列表/表单配置去掉"默认校区":接口不再返回该字段，留着会渲染空列。
-- 机构字段的 onChange 钩子只为联动刷新校区选项而存在，一并去掉。
UPDATE sys_list_config SET columns_json =
'[{"prop":"username","label":"账号","width":150},{"prop":"displayName","label":"显示名称","minWidth":140},{"prop":"institutionId","label":"机构","minWidth":140,"renderer":"institutionName"},{"prop":"phone","label":"手机号","width":140},{"prop":"roleCodes","label":"角色","minWidth":160,"type":"tags"},{"prop":"status","label":"状态","width":120,"type":"dict","dictType":"common_status","tagTypeMap":{"active":"success","disabled":"info"}},{"prop":"passwordResetRequired","label":"密码状态","width":140,"renderer":"passwordStatus"},{"prop":"lastLoginAt","label":"最近登录","width":180,"type":"datetime"},{"prop":"remark","label":"备注","minWidth":180}]'
WHERE resource_code = 'system-users' AND deleted = 0;

UPDATE sys_list_config SET form_json = '{
  "labelWidth": "auto",
  "gridColumns": 2,
  "fields": [
    { "field": "institutionId", "label": "机构", "type": "select", "required": true, "gridSpan": 1,
      "optionsLoader": "institutions" },
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
