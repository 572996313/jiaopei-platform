-- V99: 收费台账业务域（收费项目 / 价目 / 应收台账 / 收款流水 / 逐月发生 / 月度出勤）
--
-- 背景：财务目前用手工 xlsx 维护「收费出勤表」（backend/退费明细.xlsx），
-- 口径是「预收学费 → 按出勤逐月消耗 → 期末剩结余可退」。系统原本没有任何费用表，
-- 本脚本从零建 7 张表，并给家长档案补一列「支付宝绑定电话」。
--
-- 计费口径（与源表逐格核对过）：
--   应收 J   = 收费月数 × 月标准 − 上期结余 − 其他抵扣
--   未收费 L = 应收 − 实收（负数=预收/多收，合法）
--   按天项目当月发生 = 出勤天数 × 日标准
--   按月项目当月发生 = 月标准 × 月份系数（8月半月=0.50；当月无出勤默认 0）
--   结余 V   = 实收 − 合计发生，即可退金额
-- 派生值（应收/实收/未收/合计发生/结余）一律由 Service 现算，不落库，避免台账与流水两本账。
--
-- 兼容自查：普通 DDL，一条语句一条路径；无 CTE、无窗口函数、无 JSON_TABLE、无 IF( 函数；
-- 所有字符列显式 COLLATE=utf8mb4_unicode_ci；表/列全部中文 COMMENT。
-- 唯一键均含 deleted（配合 @TableLogic），但"删除后重建同键"只容得下一个墓碑，
-- 因此可重建同键的档案表在删除入口**先查引用、无引用才物理删除**：
--   收费项目被价目或台账明细引用时拒绝删除（否则外键 1451）；价目无人引用，直接物理删；
--   台账有收款或逐月发生额时拒绝删除；收款/逐月/月度出勤等流水不建议删，走冲正或重算。

CREATE TABLE `jp_fee_item` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
  `item_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收费项目编码',
  `item_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收费项目名称(保教费/伙食费/延时费…)',
  `billing_mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'monthly' COMMENT '计费方式(monthly=按月,daily=按天)',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序号(决定导出矩阵块内的项目行序)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_fee_item_code` (`institution_id`,`item_code`,`deleted`),
  KEY `idx_jp_fee_item_sort` (`institution_id`,`status`,`sort_no`),
  CONSTRAINT `fk_jp_fee_item_institution` FOREIGN KEY (`institution_id`) REFERENCES `sys_institution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费项目';

CREATE TABLE `jp_fee_price` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
  `fee_item_id` bigint NOT NULL COMMENT '收费项目ID',
  `campus_id` bigint NOT NULL DEFAULT '0' COMMENT '校区ID(0=不限校区)',
  `class_id` bigint NOT NULL DEFAULT '0' COMMENT '班级ID(0=不限班级)',
  `term_id` bigint NOT NULL DEFAULT '0' COMMENT '学期ID(0=不限学期)',
  `student_id` bigint NOT NULL DEFAULT '0' COMMENT '学员ID(0=不限学员,非0=学员级覆盖)',
  `monthly_price` decimal(10,2) DEFAULT NULL COMMENT '月标准(应收=收费月数×月标准,NULL=该维度不提供)',
  `daily_price` decimal(10,2) DEFAULT NULL COMMENT '日标准(按天项目当月发生=出勤天数×日标准,NULL=该维度不提供)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_fee_price_scope` (`institution_id`,`fee_item_id`,`campus_id`,`class_id`,`term_id`,`student_id`,`deleted`),
  KEY `idx_jp_fee_price_item` (`institution_id`,`fee_item_id`,`status`),
  CONSTRAINT `fk_jp_fee_price_institution` FOREIGN KEY (`institution_id`) REFERENCES `sys_institution` (`id`),
  CONSTRAINT `fk_jp_fee_price_item` FOREIGN KEY (`fee_item_id`) REFERENCES `jp_fee_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费价目(校区/班级/学期/学员四个维度,0=不限;四维度列不建外键,0 哨兵指不到任何行)';

CREATE TABLE `jp_fee_ledger` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
  `campus_id` bigint NOT NULL COMMENT '校区ID(创建时由班级带入,之后不可改)',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `term_id` bigint NOT NULL COMMENT '学期ID(收费是学期维度,必填)',
  `student_id` bigint NOT NULL COMMENT '学员ID',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=在收,settled=已结清)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注(导出矩阵的学员块备注)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_fee_ledger_student` (`institution_id`,`term_id`,`class_id`,`student_id`,`deleted`),
  KEY `idx_jp_fee_ledger_scope` (`institution_id`,`campus_id`,`term_id`,`class_id`),
  KEY `idx_jp_fee_ledger_student` (`institution_id`,`student_id`,`term_id`),
  CONSTRAINT `fk_jp_fee_ledger_institution` FOREIGN KEY (`institution_id`) REFERENCES `sys_institution` (`id`),
  CONSTRAINT `fk_jp_fee_ledger_campus` FOREIGN KEY (`campus_id`) REFERENCES `jp_campus` (`id`),
  CONSTRAINT `fk_jp_fee_ledger_class` FOREIGN KEY (`class_id`) REFERENCES `jp_class` (`id`),
  CONSTRAINT `fk_jp_fee_ledger_term` FOREIGN KEY (`term_id`) REFERENCES `jp_term` (`id`),
  CONSTRAINT `fk_jp_fee_ledger_student` FOREIGN KEY (`student_id`) REFERENCES `jp_student` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员收费档案(台账头)';

CREATE TABLE `jp_fee_ledger_item` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
  `campus_id` bigint NOT NULL COMMENT '校区ID(冗余,供导出与数据范围收窄)',
  `ledger_id` bigint NOT NULL COMMENT '台账ID',
  `fee_item_id` bigint NOT NULL COMMENT '收费项目ID',
  `billing_mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计费方式快照(monthly=按月,daily=按天)',
  `billable_months` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '收费月数(可为小数,如 5.5)',
  `monthly_standard` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '收费标准-月标准快照',
  `daily_rate` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '日标准快照(按天项目用)',
  `opening_balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '上期结余(正=预收,负=欠费)',
  `other_deduction` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '其他抵扣',
  `price_source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '取价来源(PRICE=价目表,STUDENT=学员覆盖,MANUAL=手工)',
  `price_id` bigint DEFAULT NULL COMMENT '取价来源的价目行ID',
  `carry_from_item_id` bigint DEFAULT NULL COMMENT '上期结余来源明细ID(结转凭证)',
  `carry_at` datetime DEFAULT NULL COMMENT '结转时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_fee_ledger_item` (`ledger_id`,`fee_item_id`,`deleted`),
  KEY `idx_jp_fee_ledger_item_scope` (`institution_id`,`campus_id`,`fee_item_id`),
  CONSTRAINT `fk_jp_fee_ledger_item_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `jp_fee_ledger` (`id`),
  CONSTRAINT `fk_jp_fee_ledger_item_item` FOREIGN KEY (`fee_item_id`) REFERENCES `jp_fee_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应收台账明细(每学员每收费项目一行)';

CREATE TABLE `jp_fee_payment` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
  `campus_id` bigint NOT NULL COMMENT '校区ID(冗余)',
  `ledger_id` bigint NOT NULL COMMENT '台账ID',
  `ledger_item_id` bigint NOT NULL COMMENT '台账明细ID',
  `payment_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收款单号',
  `amount` decimal(12,2) NOT NULL COMMENT '收款金额(正数;负数=冲正/退回)',
  `paid_on` date NOT NULL COMMENT '收款日期',
  `payment_method` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'cash' COMMENT '收款方式字典(fee_payment_method:cash=现金,wechat=微信,alipay=支付宝,bank=银行转账,other=其他)',
  `billing_month` date DEFAULT NULL COMMENT '归属账期(当月1日,可空)',
  `voucher_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '凭证号/银行流水号',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注(如 按月收)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_fee_payment_no` (`institution_id`,`payment_no`,`deleted`),
  KEY `idx_jp_fee_payment_item` (`ledger_item_id`,`paid_on`),
  KEY `idx_jp_fee_payment_scope` (`institution_id`,`campus_id`,`paid_on`),
  CONSTRAINT `fk_jp_fee_payment_institution` FOREIGN KEY (`institution_id`) REFERENCES `sys_institution` (`id`),
  CONSTRAINT `fk_jp_fee_payment_campus` FOREIGN KEY (`campus_id`) REFERENCES `jp_campus` (`id`),
  CONSTRAINT `fk_jp_fee_payment_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `jp_fee_ledger` (`id`),
  CONSTRAINT `fk_jp_fee_payment_ledger_item` FOREIGN KEY (`ledger_item_id`) REFERENCES `jp_fee_ledger_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收款流水(同一台账明细可多笔)';

CREATE TABLE `jp_fee_monthly` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
  `campus_id` bigint NOT NULL COMMENT '校区ID(冗余)',
  `ledger_id` bigint NOT NULL COMMENT '台账ID',
  `ledger_item_id` bigint NOT NULL COMMENT '台账明细ID',
  `billing_month` date NOT NULL COMMENT '账期(当月1日,如 2026-08-01)',
  `month_factor` decimal(4,2) NOT NULL DEFAULT '1.00' COMMENT '月数系数(1.00=整月,0.50=半月;按天项目不使用)',
  `auto_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '引擎自动金额(保留对账与回显)',
  `occurrence_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '最终发生额(覆盖后)',
  `manual_override` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否手工覆盖金额(0=否,1=是)',
  `override_by` bigint DEFAULT NULL COMMENT '覆盖人ID',
  `override_at` datetime DEFAULT NULL COMMENT '覆盖时间',
  `override_reason` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '覆盖原因',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_fee_monthly` (`ledger_item_id`,`billing_month`,`deleted`),
  KEY `idx_jp_fee_monthly_scope` (`institution_id`,`campus_id`,`billing_month`),
  CONSTRAINT `fk_jp_fee_monthly_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `jp_fee_ledger` (`id`),
  CONSTRAINT `fk_jp_fee_monthly_ledger_item` FOREIGN KEY (`ledger_item_id`) REFERENCES `jp_fee_ledger_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逐月发生额(按台账明细×账期,支持手工覆盖)';

CREATE TABLE `jp_fee_attendance_month` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
  `campus_id` bigint NOT NULL COMMENT '校区ID(冗余)',
  `ledger_id` bigint NOT NULL COMMENT '台账ID',
  `billing_month` date NOT NULL COMMENT '账期(当月1日)',
  `attendance_days` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '生效出勤天数(可手工改)',
  `auto_days` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '考勤汇总自动值(保留对照)',
  `manual_override` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否手工覆盖(0=否,1=是)',
  `override_by` bigint DEFAULT NULL COMMENT '覆盖人ID',
  `override_at` datetime DEFAULT NULL COMMENT '覆盖时间',
  `override_reason` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '覆盖原因',
  `source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AUTO' COMMENT '来源(AUTO=考勤汇总,MANUAL=手工,IMPORT=导入)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jp_fee_attendance_month` (`ledger_id`,`billing_month`,`deleted`),
  KEY `idx_jp_fee_attendance_month_scope` (`institution_id`,`campus_id`,`billing_month`),
  CONSTRAINT `fk_jp_fee_attendance_month_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `jp_fee_ledger` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度出勤天数(学员×账期,按天收费项目共用)';

-- 家长档案补「支付宝绑定电话」：源表 C9/D9 一列，现有表只有 parent_phone/wechat_no
ALTER TABLE `jp_student_parent`
  ADD COLUMN `alipay_phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付宝绑定电话' AFTER `wechat_no`;
