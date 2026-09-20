-- V14: Drop jp_user_token table and add sys_system_config table
-- Token storage migrated to Redis; sys_system_config added for system-level configuration

-- Drop old token table (no longer needed after Redis migration)
DROP TABLE IF EXISTS jp_user_token;

-- System configuration table
CREATE TABLE sys_system_config (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    config_key VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值',
    value_type VARCHAR(32) NOT NULL COMMENT '值类型：string/int/boolean/json',
    config_group VARCHAR(64) NOT NULL COMMENT '配置分组：auth/system/business',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    description VARCHAR(500) NULL COMMENT '配置说明',
    default_value TEXT NULL COMMENT '默认值',
    is_system TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否系统配置（系统配置不可删除）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_config_group (config_group),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数配置表';

-- Initial system configurations (auth group)
INSERT IGNORE INTO sys_system_config (id, config_key, config_value, value_type, config_group, display_name, description, default_value, is_system, sort_order, status, deleted) VALUES
(1, 'auth.password.force_change_enabled', 'true', 'boolean', 'auth', '强制改密总开关', '全局控制是否启用强制改密拦截，关闭后忽略用户级 forceChangePassword 标记', 'true', 1, 10, 'active', 0),
(2, 'auth.password.force_change_on_first_login', 'true', 'boolean', 'auth', '首次登录强制改密', '新建用户或管理员重置密码后，是否自动标记为强制改密', 'true', 1, 20, 'active', 0),
(3, 'auth.password.strength_level', 'medium', 'string', 'auth', '密码强度等级', 'simple=6-20位无要求 | medium=8-20位数字+字母 | complex=10-20位大小写+数字+特殊字符', 'medium', 1, 30, 'active', 0),
(4, 'auth.password.min_length', '8', 'int', 'auth', '密码最小长度', '新密码最少字符数', '8', 1, 40, 'active', 0),
(5, 'auth.password.max_length', '128', 'int', 'auth', '密码最大长度', '新密码最多字符数', '128', 1, 50, 'active', 0),
(6, 'auth.password.expire_days', '0', 'int', 'auth', '密码过期天数', '密码定期过期策略，0=不过期', '0', 1, 60, 'active', 0),
(7, 'auth.token.expire_hours', '12', 'int', 'auth', 'Token 有效期（小时）', 'Redis token 的 TTL 时长', '12', 1, 70, 'active', 0);

-- Add system config menu entry
INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, menu_type, path, permission_code, icon, sort_no, status, deleted) VALUES
(6, 1, 'system_config', '系统参数', 'menu', '/system/config', 'system:config:manage', 'Setting', 60, 'active', 0);

-- Grant system config permission to PLATFORM_ADMIN role
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
(6, 1, 6, 0);
