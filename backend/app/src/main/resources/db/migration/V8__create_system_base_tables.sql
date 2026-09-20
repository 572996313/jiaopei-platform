ALTER TABLE jp_user_account
    ADD COLUMN password_updated_at DATETIME NULL AFTER password_reset_required,
    ADD COLUMN force_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER password_updated_at;

UPDATE jp_user_account
SET force_change_password = password_reset_required
WHERE deleted = 0;

CREATE TABLE sys_menu (
    id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    menu_type VARCHAR(32) NOT NULL DEFAULT 'menu',
    path VARCHAR(255) NULL,
    component VARCHAR(255) NULL,
    permission_code VARCHAR(128) NULL,
    icon VARCHAR(128) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    visible TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_code (menu_code),
    KEY idx_sys_menu_parent (parent_id),
    KEY idx_sys_menu_permission (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_role_menu (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_menu (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id),
    CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (role_id) REFERENCES jp_role (id),
    CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_dict_type (
    id BIGINT NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_dict_item (
    id BIGINT NOT NULL,
    type_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item_code (type_id, item_code),
    CONSTRAINT fk_sys_dict_item_type FOREIGN KEY (type_id) REFERENCES sys_dict_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (1, NULL, 'system', 'System', 'catalog', '/system', NULL, NULL, 'setting', 100, 1, 'active', 'System root menu', 0),
    (2, 1, 'system_users', 'Users', 'menu', '/system/users', 'system/users', 'system:user:manage', 'user', 110, 1, 'active', 'User management', 0),
    (3, 1, 'system_roles', 'Roles', 'menu', '/system/roles', 'system/roles', 'system:role:manage', 'team', 120, 1, 'active', 'Role management', 0),
    (4, 1, 'system_menus', 'Menus', 'menu', '/system/menus', 'system/menus', 'system:menu:manage', 'menu', 130, 1, 'active', 'Menu management', 0),
    (5, 1, 'system_dicts', 'Dictionaries', 'menu', '/system/dicts', 'system/dicts', 'system:dict:manage', 'book', 140, 1, 'active', 'Dictionary management', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1, 1, 1, 0),
    (2, 1, 2, 0),
    (3, 1, 3, 0),
    (4, 1, 4, 0),
    (5, 1, 5, 0);

INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES (
    1, 'common_status', 'Common Status', 'active', 'Common active/disabled status', 0
);

INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (1, 1, 'active', 'Active', 'active', 10, 'active', 'Active status', 0),
    (2, 1, 'disabled', 'Disabled', 'disabled', 20, 'active', 'Disabled status', 0);

UPDATE jp_role
SET permission_codes = 'organization:read,organization:write,education:read,education:write,system:user:manage,system:role:manage,system:menu:manage,system:dict:manage'
WHERE role_code = 'PLATFORM_ADMIN'
  AND deleted = 0;
