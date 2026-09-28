-- ============================================================================
-- V88：实体编号生成规则
--
-- 与 cornerstone-core jar 内的 cornerstone/migration/V108__create_sys_code_rule.sql
-- 是同一批改动的两份落点，建表语句一字不差（AGENTS「模块装配与开关」的双轨规则）：
-- 教培自己的库从 V1..V87 长起来，cornerstone/migration 那条目录不在本仓库的
-- cornerstone.flyway.locations 里，所以 V108 在教培一条都不会执行；反过来新项目
-- 只跑 V1xx，拿不到本文件。少写任何一份，另一条路径就缺这张表。
--
-- 设计口径（改这张表前先读，几条是成套的）：
--   1. **一个资源一条规则，resource_code 是唯一键，不是 id**。资源清单由各模块用
--      CodeRuleContributor 在代码里登记（含"这个资源的编号存在哪张表哪一列"、默认格式），
--      表里只存操作员改过的生效格式与开关。所以只有 UPSERT、没有新增/删除：
--      不写行 = 未启用 = 留空照样报"编码不能为空"；写了行就整份存生效格式。
--      **不用"空串=取默认"的哨兵**：分隔符留空（各段直接相连）与日期段留空（不带日期段、
--      序号全局累加）本身就是合法取值，再让它们兼任"回落默认"就分不清了。
--      代码登记的默认值只用于维护页表单首次预填。
--   2. **只按资源分条，不按机构分条**（一期口径：全站一套规则）。日后要按机构各配一套，
--      唯一键改成 (institution_id, resource_code, deleted)，别让客户端传 institution_id。
--   3. **不设"序号作用域"字段**：日期段的粒度天然就是作用域。date_pattern=yyyyMMdd 即每天
--      从 001 重排，yyyy 即全年累加，空即全局累加——取号只在"同一个日期段前缀"里找最大值。
--      再加一个 scope 列会和日期段互相矛盾。
--   4. 允许断号（现场口径：编号只是快捷入口，客户自己维护、不要求连号），所以不建序列表、
--      不加行锁；并发取到同一个号由各资源的业务唯一键挡着，取号侧再逐个让号。
--   5. 表名与列名**不存这里**，存代码登记处：存进库等于让运维把生成器指向任意一张表，
--      而这两条名字在 SQL 里是拼接而非绑定参数（只接受启动期登记的白名单，见 CodeRuleRegistry）。
--   6. 分隔符列名是 separator_char 而不是 separator：**SEPARATOR 是 MySQL 保留字**。
--      MyBatis-Plus 拼 wrapper 的列名与"列名 AS 属性名"的别名都不加反引号，两边只要有一边撞上
--      保留字就整片 1064，所以实体字段名也一并叫 separatorChar（接口出入参仍是 separator，
--      视图与请求记录不带 MP 的映射）。守护：CodeRuleEntityMappingTest。
-- ============================================================================

CREATE TABLE `sys_code_rule` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `resource_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编码资源码,与代码登记的资源清单一一对应',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用自动编号(0=留空仍必填,1=点按钮可取号且留空即补号)',
  `prefix` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编号前缀,如 STU',
  `separator_char` varchar(4) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '段间分隔符(- 中划线,_ 下划线,. 圆点;空串=各段直接相连)',
  `date_pattern` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '日期段格式(yyyyMMdd=按日重排,yyyyMM=按月,yyyy=按年,yyMM/yyMMdd 同口径,空串=不带日期段即全局累加)',
  `serial_length` int NOT NULL COMMENT '序号位数,不足补零;实际号超出位数时自动加宽,不截断',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_code_rule_resource` (`resource_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体编号生成规则(每个资源一条)';
