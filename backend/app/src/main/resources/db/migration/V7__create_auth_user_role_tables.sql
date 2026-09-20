CREATE TABLE jp_user_account (
    id BIGINT NOT NULL,
    institution_id BIGINT NOT NULL,
    default_campus_id BIGINT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    phone VARCHAR(32) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    password_reset_required TINYINT(1) NOT NULL DEFAULT 0,
    last_login_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_user_username (username),
    KEY idx_jp_user_institution (institution_id),
    CONSTRAINT fk_jp_user_institution FOREIGN KEY (institution_id) REFERENCES jp_institution (id),
    CONSTRAINT fk_jp_user_default_campus FOREIGN KEY (default_campus_id) REFERENCES jp_campus (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE jp_role (
    id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    permission_codes TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE jp_user_role_rel (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_user_role (user_id, role_id),
    CONSTRAINT fk_jp_user_role_user FOREIGN KEY (user_id) REFERENCES jp_user_account (id),
    CONSTRAINT fk_jp_user_role_role FOREIGN KEY (role_id) REFERENCES jp_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE jp_user_token (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    access_token VARCHAR(128) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_user_token (access_token),
    KEY idx_jp_user_token_user (user_id),
    CONSTRAINT fk_jp_user_token_user FOREIGN KEY (user_id) REFERENCES jp_user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO jp_institution (
    id, org_code, org_name, short_name, contact_name, contact_phone,
    province_code, city_code, district_code, address, timezone, status,
    opened_at, remark, deleted
) VALUES (
    1, 'SYS-DEFAULT', '系统默认机构', '默认机构', 'system', NULL,
    NULL, NULL, NULL, 'system', 'Asia/Shanghai', 'active',
    NULL, 'Initial institution for first admin account', 0
);

INSERT IGNORE INTO jp_user_account (
    id, institution_id, default_campus_id, username, password_hash,
    display_name, phone, status, password_reset_required, remark, deleted
) VALUES (
    1, 1, NULL, 'admin', '{noop}Admin@123456',
    '平台管理员', NULL, 'active', 1, 'Initial admin account, change password after first login', 0
);

INSERT IGNORE INTO jp_role (
    id, role_code, role_name, permission_codes, status, remark, deleted
) VALUES (
    1,
    'PLATFORM_ADMIN',
    '平台管理员',
    'organization:read,organization:write,education:read,education:write,system:user:manage,system:role:manage',
    'active',
    'Initial platform admin role',
    0
);

INSERT IGNORE INTO jp_user_role_rel (
    id, user_id, role_id, deleted
) VALUES (
    1, 1, 1, 0
);
