-- ============================================================================
-- V46：注册凭证（邀请码 / 激活码）—— 与底座基线 cornerstone/migration/V103 对齐
--
-- 教培走存量路径（V1..V45），底座新能力要在本仓库补一份同内容脚本，
-- 保证"两条路径长出同一个库"（口径见 V45 头注释与 CornerstoneBaselineGuardTest）。
-- 建表/注释/种子与 V103 一字不差。
-- ============================================================================

CREATE TABLE `sys_auth_invitation` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '归属机构ID',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '凭证码(INV前缀=邀请码,ACT前缀=激活码)',
  `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '凭证类型(INVITE=邀请注册,ACTIVATE=激活预建账号)',
  `user_id` bigint DEFAULT NULL COMMENT '待激活账号ID(ACTIVATE必填,INVITE为空)',
  `role_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '注册成功后自动分配的角色编码(INVITE必填,ACTIVATE忽略)',
  `max_uses` int DEFAULT NULL COMMENT '最大可用次数(空为不限)',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '已使用次数',
  `expires_at` datetime DEFAULT NULL COMMENT '过期时间(空为不过期)',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_auth_invitation_code` (`code`,`deleted`),
  KEY `idx_sys_auth_invitation_institution` (`institution_id`),
  KEY `idx_sys_auth_invitation_user` (`user_id`),
  CONSTRAINT `fk_sys_auth_invitation_institution` FOREIGN KEY (`institution_id`) REFERENCES `sys_institution` (`id`),
  CONSTRAINT `fk_sys_auth_invitation_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号注册凭证(邀请码/激活码)';

-- 注册来源补一种:邀请码自助注册(列定义照现状原样抄,只改注释)
ALTER TABLE `sys_user_account`
  MODIFY COLUMN `register_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADMIN' COMMENT '注册来源(ADMIN=后台创建,WECHAT_MINIAPP=小程序自助注册,INVITATION_CODE=邀请码自助注册)';

-- 注册凭证管理页与操作权限(页挂"机构与权限"目录下)
INSERT INTO `sys_menu` (`id`, `portal`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `permission_code`, `icon`, `sort_no`, `visible`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted`) VALUES (18,'admin',12,'system_invitations','注册凭证','menu','/system/invitations','system/invitations',NULL,'Key',30,1,'active','注册凭证管理页','2026-09-22 00:00:00',NULL,'2026-09-22 00:00:00',NULL,0);
INSERT INTO `sys_menu` (`id`, `portal`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `permission_code`, `icon`, `sort_no`, `visible`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted`) VALUES (115,'admin',18,'system_invitations_manage','注册凭证维护','button',NULL,NULL,'system:invitation:manage',NULL,10,0,'active','注册凭证页操作权限','2026-09-22 00:00:00',NULL,'2026-09-22 00:00:00',NULL,0);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted`) VALUES (2101,1,18,'2026-09-22 00:00:00',NULL,'2026-09-22 00:00:00',NULL,0);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted`) VALUES (2102,1,115,'2026-09-22 00:00:00',NULL,'2026-09-22 00:00:00',NULL,0);

-- 平台管理员角色追加凭证维护权限码
UPDATE `sys_role` SET `permission_codes` = CONCAT(`permission_codes`, ',system:invitation:manage') WHERE `role_code` = 'PLATFORM_ADMIN' AND `deleted` = 0;
