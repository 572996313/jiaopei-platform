-- ============================================================================
-- V45：把教培库对齐到 Cornerstone 底座基线（cornerstone-core jar 的 V100/V101）
--
-- 阶段五-0e 把底座表结构收口成一份"新项目直接可用"的基线脚本，放在 core jar 的
-- classpath:cornerstone/migration。教培是存量项目，走的是 V7/V8/V14 一路演进的老路径，
-- 两条路径必须长出同一个库，差异在这里补齐。比对由
-- app/src/test/.../CornerstoneBaselineDriftTest 强制（结构逐列比）。
--
-- 两类改动：
--   1. 12 个唯一键一律追加 deleted 列。原来只有业务列，@TableLogic 软删后再建同名记录
--      必撞 Duplicate entry（服务层 selectCount 只看 deleted=0 会先放行、到 DB 才炸成 500）。
--      deleted 是 0/1，所以同一列最多只能留一条软删记录 —— 治标，彻底解决要改物理删除，另议。
--      三个键（uk_sys_dict_item_code / uk_sys_role_menu / uk_jp_user_role）的最左列是外键列，
--      单独 DROP INDEX 会撞 1553，因此一律用"同一条 ALTER 里删旧键 + 建同名新键"。
--   2. 52 个字段补中文注释（sys_dict_type 10、sys_dict_item 13、sys_menu 19、
--      sys_role_menu 8、sys_user_account 2）。列定义照 SHOW CREATE TABLE 原样抄，
--      MODIFY COLUMN 只有和原定义完全一致才不会隐式改列。
--
-- 幂等性：本脚本只在老库上执行一次，Flyway 不重放，所以不写 IF EXISTS 之类的守卫
--        （MySQL 也没有 ALTER TABLE DROP INDEX IF EXISTS）。
-- 全新库不需要跑本脚本：直接启用 cornerstone/migration 就是对齐后的形态。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、唯一键追加 deleted（12 个）
-- ---------------------------------------------------------------------------

ALTER TABLE `sys_institution` DROP INDEX `uk_jp_institution_org_code`, ADD UNIQUE KEY `uk_jp_institution_org_code` (`org_code`,`deleted`);
ALTER TABLE `sys_institution` DROP INDEX `uk_jp_institution_org_name`, ADD UNIQUE KEY `uk_jp_institution_org_name` (`org_name`,`deleted`);
ALTER TABLE `sys_role` DROP INDEX `uk_jp_role_code`, ADD UNIQUE KEY `uk_jp_role_code` (`role_code`,`deleted`);
ALTER TABLE `sys_menu` DROP INDEX `uk_sys_menu_code`, ADD UNIQUE KEY `uk_sys_menu_code` (`menu_code`,`deleted`);
ALTER TABLE `sys_dict_type` DROP INDEX `uk_sys_dict_type_code`, ADD UNIQUE KEY `uk_sys_dict_type_code` (`type_code`,`deleted`);
ALTER TABLE `sys_dict_item` DROP INDEX `uk_sys_dict_item_code`, ADD UNIQUE KEY `uk_sys_dict_item_code` (`type_id`,`item_code`,`deleted`);
ALTER TABLE `sys_role_menu` DROP INDEX `uk_sys_role_menu`, ADD UNIQUE KEY `uk_sys_role_menu` (`role_id`,`menu_id`,`deleted`);
ALTER TABLE `sys_user_role_rel` DROP INDEX `uk_jp_user_role`, ADD UNIQUE KEY `uk_jp_user_role` (`user_id`,`role_id`,`deleted`);
ALTER TABLE `sys_data_source` DROP INDEX `uk_data_source_code`, ADD UNIQUE KEY `uk_data_source_code` (`source_code`,`deleted`);
ALTER TABLE `sys_list_config` DROP INDEX `uk_list_config_code`, ADD UNIQUE KEY `uk_list_config_code` (`resource_code`,`deleted`);
ALTER TABLE `sys_system_config` DROP INDEX `uk_config_key`, ADD UNIQUE KEY `uk_config_key` (`config_key`,`deleted`);
ALTER TABLE `sys_user_account` DROP INDEX `uk_jp_user_username`, ADD UNIQUE KEY `uk_jp_user_username` (`username`,`deleted`);

-- ---------------------------------------------------------------------------
-- 二、补齐字段中文注释（52 个）
-- ---------------------------------------------------------------------------

ALTER TABLE `sys_dict_type`
  MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
  MODIFY COLUMN `type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码(唯一)',
  MODIFY COLUMN `type_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型名称',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)';

ALTER TABLE `sys_dict_item`
  MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
  MODIFY COLUMN `type_id` bigint NOT NULL COMMENT '所属字典类型ID',
  MODIFY COLUMN `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项编码(同类型内唯一)',
  MODIFY COLUMN `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项名称(展示用)',
  MODIFY COLUMN `item_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项值(存库用)',
  MODIFY COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)';

ALTER TABLE `sys_menu`
  MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
  MODIFY COLUMN `portal` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'admin' COMMENT '菜单归属端(admin=管理后台,业务端由 PortalResolver 扩展)',
  MODIFY COLUMN `parent_id` bigint DEFAULT NULL COMMENT '父菜单ID',
  MODIFY COLUMN `menu_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单编码(唯一)',
  MODIFY COLUMN `menu_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称',
  MODIFY COLUMN `menu_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'menu' COMMENT '菜单类型(catalog=目录,menu=菜单/页面,button=按钮)',
  MODIFY COLUMN `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端路由路径',
  MODIFY COLUMN `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端组件路径',
  MODIFY COLUMN `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限码(按钮节点承载API权限)',
  MODIFY COLUMN `icon` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标名称',
  MODIFY COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  MODIFY COLUMN `visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可见',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)';

ALTER TABLE `sys_role_menu`
  MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
  MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '角色ID',
  MODIFY COLUMN `menu_id` bigint NOT NULL COMMENT '菜单ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)';

ALTER TABLE `sys_user_account`
  MODIFY COLUMN `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '密码({bcrypt}=加密,其余编码前缀= DelegatingPasswordEncoder 识别,裸值为明文)',
  MODIFY COLUMN `register_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADMIN' COMMENT '注册来源(ADMIN=后台创建,WECHAT_MINIAPP=小程序自助注册)';
