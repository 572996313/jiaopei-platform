-- V33: Create sys_data_source for configurable option data sources (low-code style)
-- 把「页面下拉/多选的选项数据源」从代码注册表(renderers.ts optionLoaders)收敛为可配置管理:
-- 数据源编码 → 接口路径 → value/label 字段,改接口只需改这一条配置,前端通用组件按编码消费。
-- 消费接口:GET /api/system/data-sources/definitions?codes=a,b (仅需登录)
-- 管理接口:GET/POST/PUT/DELETE /api/system/data-sources (需 system:data-source:manage)

CREATE TABLE sys_data_source (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    source_code VARCHAR(64) NOT NULL COMMENT '数据源编码,唯一标识,如 institutions/roles',
    source_name VARCHAR(64) NOT NULL COMMENT '数据源名称,如 机构列表',
    endpoint VARCHAR(128) NOT NULL COMMENT '接口路径,如 /organizations/institutions',
    method VARCHAR(16) NOT NULL DEFAULT 'GET' COMMENT '请求方法(default=GET)',
    value_key VARCHAR(64) NULL COMMENT '选项值字段,缺省为 id',
    label_key VARCHAR(64) NULL COMMENT '选项标签字段,缺省为名称类字段;支持 point 语法如 orgName',
    param_defs TEXT NULL COMMENT '请求参数定义 JSON 数组,如 [{"name":"institutionId","required":false}]',
    source_type VARCHAR(32) NOT NULL DEFAULT 'api' COMMENT '数据源类型(api=接口数据,预留 dict=字典)',
    dict_type VARCHAR(64) NULL COMMENT 'source_type=dict 时的字典类型编码',
    description VARCHAR(500) NULL COMMENT '说明',
    status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    UNIQUE KEY uk_data_source_code (source_code),
    INDEX idx_data_source_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选项数据源配置(页面下拉选项的可配置数据源)';

-- 种子数据:对齐既有前端 optionLoaders 注册表(renderers.ts),零切换成本。
-- 机构列表:label=orgName;校区列表:label=campusName(依赖 institutionId 参数);
-- 学期:label=termName;教室:label=classroomName;时段:label=slotName;角色:label=roleName。
INSERT IGNORE INTO sys_data_source (
    id, source_code, source_name, endpoint, method, value_key, label_key, param_defs,
    source_type, dict_type, description, status, remark, deleted
) VALUES
    (1, 'institutions', '机构列表', '/organizations/institutions', 'GET', 'id', 'orgName', NULL,
     'api', NULL, '机构下拉选项(用户/表单的机构选择)', 'active', '内置数据源,对应 /api/organizations/institutions', 0),
    (2, 'campuses', '校区列表', '/organizations/campuses', 'GET', 'id', 'campusName',
     '[{"name":"institutionId","required":false}]',
     'api', NULL, '校区下拉选项,可按机构过滤', 'active', '内置数据源,对应 /api/organizations/campuses', 0),
    (3, 'terms', '学期列表', '/organizations/terms', 'GET', 'id', 'termName',
     '[{"name":"institutionId","required":false}]',
     'api', NULL, '学期下拉选项,可按机构过滤', 'active', '内置数据源,对应 /api/organizations/terms', 0),
    (4, 'classrooms', '教室列表', '/organizations/classrooms', 'GET', 'id', 'classroomName',
     '[{"name":"campusId","required":false}]',
     'api', NULL, '教室下拉选项,可按校区过滤', 'active', '内置数据源,对应 /api/organizations/classrooms', 0),
    (5, 'timeSlots', '时段列表', '/organizations/time-slots', 'GET', 'id', 'slotName',
     '[{"name":"institutionId","required":false}]',
     'api', NULL, '上课时段下拉选项,可按机构过滤', 'active', '内置数据源,对应 /api/organizations/time-slots', 0),
    (6, 'roles', '角色列表', '/system/roles', 'GET', 'id', 'roleName', NULL,
     'api', NULL, '角色下拉选项(用户管理的角色选择)', 'active', '内置数据源,对应 /api/system/roles', 0);

-- 菜单节点:id 11 follows V32(10);按钮 id 111 follows V32(110);sys_role_menu id 21/22 follows V32(19-20)。
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (11, 'admin', 1, 'system_data_sources', '数据源配置', 'menu', '/system/data-sources', 'system/data-sources',
     NULL, 'connection', 175, 1, 'active', '页面下拉选项的数据源配置', 0);

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (111, 'admin', 11, 'system_data_sources_manage', '数据源配置维护', 'button', NULL, NULL,
     'system:data-source:manage', NULL, 10, 0, 'active', '数据源配置页操作权限', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (21, 1, 11, 0),
    (22, 1, 111, 0);

-- 追加控制器权限码到 PLATFORM_ADMIN 角色(与既有权限合并,避免重复)
UPDATE jp_role
SET permission_codes = TRIM(BOTH ',' FROM CONCAT_WS(',', permission_codes, 'system:data-source:manage'))
WHERE role_code = 'PLATFORM_ADMIN'
  AND deleted = 0
  AND (permission_codes IS NULL OR permission_codes NOT LIKE '%system:data-source:manage%');