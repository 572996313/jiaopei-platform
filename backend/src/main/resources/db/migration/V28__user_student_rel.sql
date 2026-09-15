-- 微信小程序:用户-学员档案关联(学生/家长均可绑定学员),以及小程序角色补充权限码

-- 学生/家长账号与学员档案的关联(一个账号可绑多个学员;一个学员可被多个家长账号关联)
CREATE TABLE jp_user_student_rel (
    id BIGINT NOT NULL,
    institution_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_user_student (user_id, student_id),
    KEY idx_jp_user_student_student (student_id),
    CONSTRAINT fk_jp_user_student_user FOREIGN KEY (user_id) REFERENCES jp_user_account (id),
    CONSTRAINT fk_jp_user_student_student FOREIGN KEY (student_id) REFERENCES jp_student (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 小程序角色增加访问小程序功能的权限码
UPDATE jp_role SET permission_codes = TRIM(BOTH ',' FROM CONCAT_WS(',', permission_codes, 'miniapp:access'))
WHERE role_code IN ('MINIAPP_STUDENT', 'MINIAPP_PARENT') AND deleted = 0;