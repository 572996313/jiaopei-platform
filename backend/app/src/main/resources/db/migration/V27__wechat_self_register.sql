-- 微信小程序自助注册:登录用户无密码,register_source 区分账号来源,预置学生/家长两个小程序角色

ALTER TABLE jp_user_account MODIFY password_hash VARCHAR(255) NULL;

ALTER TABLE jp_user_account ADD COLUMN register_source VARCHAR(32) NOT NULL DEFAULT 'ADMIN';

INSERT IGNORE INTO jp_role (id, role_code, role_name, permission_codes, status, remark, deleted) VALUES
(101, 'MINIAPP_STUDENT', '学生端用户', 'miniapp:base', 'active', '微信小程序自助注册-学生身份', 0),
(102, 'MINIAPP_PARENT', '家长端用户', 'miniapp:base', 'active', '微信小程序自助注册-家长身份', 0);
