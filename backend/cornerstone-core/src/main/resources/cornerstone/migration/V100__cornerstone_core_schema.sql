-- ============================================================================
-- V100：Cornerstone 底座基线（结构）
--
-- 归属：cornerstone-core jar。新项目把 cornerstone.flyway.locations 配成
--       classpath:db/migration,classpath:cornerstone/migration 即可获得本基线；
--       教培仓库不走这条路（它的 sys_ 表在 V7/V8/V14 一路演进到 V45，
--       两条路径必须长出同一个库，差异由 app/src/test 的漂移比对测试拦着）。
-- 号段：V100~V199 归底座，项目自己的业务脚本从 V200 起（重版本号 Flyway 直接启动失败）。
--
-- 与教培库逐字段对齐后，本脚本相对"教培 V1..V44 长出来的库"有三处**有意**的差异，
-- 全部由教培侧 V45 同步补齐，所以两条路径的最终形态一致：
--   1. 12 个唯一键一律带上 deleted 列（原来只有业务列，软删后重建同名记录必撞键，
--      见 AGENTS.md「逻辑删除与唯一索引」）。注意 deleted 是 0/1，同一列最多只允许
--      留一条软删记录，这是治标不是治本；要彻底解决得改物理删除，另议。
--   2. sys_dict_type / sys_dict_item / sys_menu / sys_role_menu 的全部字段、
--      sys_user_account 的 password_hash 与 register_source 补齐中文注释（原来缺失）。
--   3. 每张表都显式写 COLLATE=utf8mb4_unicode_ci，不依赖服务器默认
--      （MySQL 8.4 服务器默认是 utf8mb4_0900_ai_ci，V14 当年漏写就漂移过一次，V44 才收口）。
--
-- 索引/外键的键名保持历史 jp_ 前缀不动（0b 只改了表名），改名对运行期没好处、
-- 还会让两条路径的比对多出无意义差异。
-- ============================================================================

CREATE TABLE `sys_institution` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `org_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '机构编码',
  `org_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '机构名称',
  `short_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构简称',
  `contact_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `province_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '省份编码',
  `city_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '城市编码',
  `district_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '区县编码',
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '详细地址',
  `timezone` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '状态 draft草稿',
  `opened_at` date DEFAULT NULL COMMENT '开业日期',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_institution_org_code` (`org_code`,`deleted`),
  UNIQUE KEY `uk_jp_institution_org_name` (`org_name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训机构';

CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `role_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `role_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `permission_codes` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限码列表',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_role_code` (`role_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `portal` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'admin' COMMENT '菜单归属端(admin=管理后台,业务端由 PortalResolver 扩展)',
  `parent_id` bigint DEFAULT NULL COMMENT '父菜单ID',
  `menu_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单编码(唯一)',
  `menu_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称',
  `menu_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'menu' COMMENT '菜单类型(catalog=目录,menu=菜单/页面,button=按钮)',
  `path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端路由路径',
  `component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端组件路径',
  `permission_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限码(按钮节点承载API权限)',
  `icon` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可见',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_code` (`menu_code`,`deleted`),
  KEY `idx_sys_menu_parent` (`parent_id`),
  KEY `idx_sys_menu_permission` (`permission_code`),
  KEY `idx_sys_menu_portal` (`portal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单';

CREATE TABLE `sys_user_account` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '密码({bcrypt}=加密,其余编码前缀= DelegatingPasswordEncoder 识别,裸值为明文)',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名称',
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
  `password_reset_required` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否需要重置密码 1是 0否',
  `password_updated_at` datetime DEFAULT NULL COMMENT '密码更新时间',
  `force_change_password` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否强制改密 1是 0否',
  `last_login_at` datetime DEFAULT NULL COMMENT '上次登录时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `register_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADMIN' COMMENT '注册来源(ADMIN=后台创建,WECHAT_MINIAPP=小程序自助注册)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_user_username` (`username`,`deleted`),
  KEY `idx_jp_user_institution` (`institution_id`),
  CONSTRAINT `fk_jp_user_institution` FOREIGN KEY (`institution_id`) REFERENCES `sys_institution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号';

CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `type_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码(唯一)',
  `type_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型名称',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`type_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型';

CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `type_id` bigint NOT NULL COMMENT '所属字典类型ID',
  `item_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项编码(同类型内唯一)',
  `item_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项名称(展示用)',
  `item_value` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项值(存库用)',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item_code` (`type_id`,`item_code`,`deleted`),
  CONSTRAINT `fk_sys_dict_item_type` FOREIGN KEY (`type_id`) REFERENCES `sys_dict_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项';

CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu` (`role_id`,`menu_id`,`deleted`),
  KEY `idx_sys_role_menu_menu` (`menu_id`),
  CONSTRAINT `fk_sys_role_menu_menu` FOREIGN KEY (`menu_id`) REFERENCES `sys_menu` (`id`),
  CONSTRAINT `fk_sys_role_menu_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关系';

CREATE TABLE `sys_user_role_rel` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_user_role` (`user_id`,`role_id`,`deleted`),
  KEY `fk_jp_user_role_role` (`role_id`),
  CONSTRAINT `fk_jp_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_jp_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关系';

CREATE TABLE `sys_data_source` (
  `id` bigint NOT NULL COMMENT '主键',
  `source_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据源编码,唯一标识,如 institutions/roles',
  `source_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据源名称,如 机构列表',
  `endpoint` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口路径(source_type=api 时必填,如 /organizations/institutions)',
  `method` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GET' COMMENT '请求方法(default=GET)',
  `value_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '选项值字段,缺省为 id',
  `label_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '选项标签字段,缺省为名称类字段;支持 point 语法如 orgName',
  `param_defs` text COLLATE utf8mb4_unicode_ci COMMENT '请求参数定义 JSON 数组,如 [{"name":"institutionId","required":false}]',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'api' COMMENT '数据源类型(api=接口数据,预留 dict=字典)',
  `dict_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'source_type=dict 时的字典类型编码',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_source_code` (`source_code`,`deleted`),
  KEY `idx_data_source_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选项数据源配置(页面下拉选项的可配置数据源)';

CREATE TABLE `sys_list_config` (
  `id` bigint NOT NULL COMMENT '主键',
  `resource_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源编码,列表页唯一标识,如 system-users',
  `resource_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源名称',
  `endpoint` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分页查询接口路径',
  `page_size` int NOT NULL DEFAULT '10' COMMENT '默认每页条数',
  `columns_json` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列配置 JSON 数组',
  `query_json` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询条件配置 JSON 数组',
  `actions_json` text COLLATE utf8mb4_unicode_ci COMMENT '按钮配置 JSON 数组,为空时前端回退插槽模式',
  `form_json` text COLLATE utf8mb4_unicode_ci COMMENT '弹窗表单配置 JSON(fields 数组),为空表示该资源未配置表单',
  `page_json` text COLLATE utf8mb4_unicode_ci COMMENT '页面壳配置 JSON(title/description/showCount/countFormat/emptyText),为空时前端回退默认壳',
  `dialog_json` text COLLATE utf8mb4_unicode_ci COMMENT '弹窗壳配置 JSON(width/createTitle/editTitle/submitText/cancelText/createMessage/updateMessage/reloadAfterSubmit),为空时前端回退默认弹窗',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态:active/disabled',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_config_code` (`resource_code`,`deleted`),
  KEY `idx_list_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可配置列表页配置表';

CREATE TABLE `sys_system_config` (
  `id` bigint NOT NULL COMMENT '主键',
  `config_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
  `value_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '值类型：string/int/boolean/json',
  `config_group` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置分组：auth/system/business',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置说明',
  `default_value` text COLLATE utf8mb4_unicode_ci COMMENT '默认值',
  `is_system` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否系统配置（系统配置不可删除）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`,`deleted`),
  KEY `idx_config_group` (`config_group`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';
