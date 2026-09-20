-- V20: Create sys_list_config for configurable list pages (columns + query conditions)
-- 通用列表组件按此配置渲染查询区/表格列/分页;系统设置的用户、角色、系统参数三个列表首批接入,
-- 种子配置与改造前手写表格的列保持一致,上线后视觉零变化。

CREATE TABLE sys_list_config (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    resource_code VARCHAR(64) NOT NULL COMMENT '资源编码,列表页唯一标识,如 system-users',
    resource_name VARCHAR(64) NOT NULL COMMENT '资源名称',
    endpoint VARCHAR(128) NOT NULL COMMENT '分页查询接口路径',
    page_size INT NOT NULL DEFAULT 10 COMMENT '默认每页条数',
    columns_json TEXT NOT NULL COMMENT '列配置 JSON 数组',
    query_json TEXT NOT NULL COMMENT '查询条件配置 JSON 数组',
    status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态:active/disabled',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uk_list_config_code (resource_code),
    INDEX idx_list_config_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可配置列表页配置表';

-- 用户管理列表配置(与改造前用户列表列一致)
INSERT IGNORE INTO sys_list_config (id, resource_code, resource_name, endpoint, page_size, columns_json, query_json, status, remark, deleted) VALUES
(1, 'system-users', '用户管理', '/system/users/page', 10,
 '[{"prop":"username","label":"账号","width":150},{"prop":"displayName","label":"显示名称","minWidth":140},{"prop":"institutionId","label":"机构","minWidth":140,"renderer":"institutionName"},{"prop":"defaultCampusId","label":"默认校区","minWidth":160,"renderer":"campusName"},{"prop":"phone","label":"手机号","width":140},{"prop":"roleCodes","label":"角色","minWidth":160,"type":"tags"},{"prop":"status","label":"状态","width":120,"type":"dict","dictType":"common_status","tagTypeMap":{"active":"success","disabled":"info"}},{"prop":"passwordResetRequired","label":"密码状态","width":140,"renderer":"passwordStatus"},{"prop":"lastLoginAt","label":"最近登录","width":180,"type":"datetime"},{"prop":"remark","label":"备注","minWidth":180}]',
 '[{"field":"keyword","label":"关键字","type":"input","placeholder":"账号/显示名称/手机号"},{"field":"status","label":"状态","type":"dict-select","dictType":"common_status"},{"field":"institutionId","label":"机构","type":"select","optionsLoader":"institutions"}]',
 'active', '系统设置-用户管理列表', 0);

-- 角色管理列表配置
INSERT IGNORE INTO sys_list_config (id, resource_code, resource_name, endpoint, page_size, columns_json, query_json, status, remark, deleted) VALUES
(2, 'system-roles', '角色管理', '/system/roles/page', 10,
 '[{"prop":"roleCode","label":"角色编码","width":180},{"prop":"roleName","label":"角色名称","minWidth":180},{"prop":"permissionCodes","label":"权限","minWidth":300,"renderer":"permissionTags"},{"prop":"status","label":"状态","width":120,"type":"dict","dictType":"common_status","tagTypeMap":{"active":"success","disabled":"info"}},{"prop":"remark","label":"备注","minWidth":200}]',
 '[{"field":"keyword","label":"关键字","type":"input","placeholder":"角色编码/角色名称"},{"field":"status","label":"状态","type":"dict-select","dictType":"common_status"}]',
 'active', '系统设置-角色管理列表', 0);

-- 系统参数列表配置
INSERT IGNORE INTO sys_list_config (id, resource_code, resource_name, endpoint, page_size, columns_json, query_json, status, remark, deleted) VALUES
(3, 'system-configs', '系统参数', '/system/config/page', 10,
 '[{"prop":"configKey","label":"配置键","minWidth":200,"showOverflowTooltip":true},{"prop":"displayName","label":"显示名称","minWidth":140},{"prop":"configValue","label":"配置值","minWidth":160,"showOverflowTooltip":true},{"prop":"valueType","label":"值类型","width":100},{"prop":"configGroup","label":"分组","width":120,"type":"map","valueMap":{"general":"通用","auth":"认证","system":"系统","business":"业务"}},{"prop":"isSystem","label":"系统内置","width":100,"type":"boolean","trueLabel":"是","falseLabel":"否"},{"prop":"sortOrder","label":"排序","width":80},{"prop":"status","label":"状态","width":110,"type":"dict","dictType":"common_status","tagTypeMap":{"active":"success","disabled":"info"}},{"prop":"description","label":"说明","minWidth":180,"showOverflowTooltip":true}]',
 '[{"field":"keyword","label":"关键字","type":"input","placeholder":"配置键/显示名称/说明"},{"field":"configGroup","label":"分组","type":"select","options":[{"label":"通用","value":"general"},{"label":"认证","value":"auth"},{"label":"系统","value":"system"},{"label":"业务","value":"business"}]},{"field":"status","label":"状态","type":"dict-select","dictType":"common_status"}]',
 'active', '系统设置-系统参数列表', 0);
