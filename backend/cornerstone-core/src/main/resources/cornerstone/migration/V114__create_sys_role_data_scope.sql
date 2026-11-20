-- V114: 角色数据范围授权表（底座通用）
-- 一条记录表示「某角色在某机构的某数据范围维度里包含某个节点」。
-- 未配置任何记录 = 该维度不限制；scope_value = 0 = 该维度全部；其余值为白名单。
-- 业务维度（如组织/教务的节点）由各模块通过 SPI 注册与校验，底座不感知具体业务含义。
--
-- 双胞胎脚本：app 的 db/migration/V98__create_sys_role_data_scope.sql，两份 DDL 一字不差。
-- 本脚本只服务「下游从零建库」，教培自己的库从 app 的 V1..V9x 长起来。

CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID',
  `scope_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据范围维度标识',
  `scope_value` bigint NOT NULL COMMENT '维度内节点ID；0=该维度全部',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_data_scope` (`role_id`,`institution_id`,`scope_type`,`scope_value`,`deleted`),
  KEY `idx_sys_role_data_scope_role` (`role_id`,`institution_id`,`scope_type`),
  CONSTRAINT `fk_sys_role_data_scope_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色数据范围授权';
