CREATE TABLE jp_wechat_binding (
    id BIGINT NOT NULL,
    openid VARCHAR(64) NOT NULL,
    union_id VARCHAR(64) NULL,
    user_id BIGINT NOT NULL,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_wechat_openid (openid),
    KEY idx_jp_wechat_user (user_id),
    CONSTRAINT fk_jp_wechat_user FOREIGN KEY (user_id) REFERENCES jp_user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
