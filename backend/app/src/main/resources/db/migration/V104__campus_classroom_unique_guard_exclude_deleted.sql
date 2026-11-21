-- V104: 校区/教室的唯一约束改为「只约束未删除行」，软删墓碑不再占住编码与名称。
-- 与 V103（学期）同一配方：未删除行取「范围ID#业务键」，墓碑取 NULL，唯一键建在守卫列上。
--
-- 起因：uk_jp_campus_code/name、uk_jp_classroom_code/name 都不含 deleted。删除走 @TableLogic
-- 逻辑删除，墓碑仍占着编码与名称，重建同名/同码必撞 1062；应用层预检走 MyBatis-Plus 会自动补
-- deleted = 0、查不到墓碑，现场看到的就是「库里删了还提示已存在」。
--
-- 不走物理删除：两张表都有大量子表外键（jp_campus 被 13 张表引用），软删子行同样占着外键（1451）。
--
-- 兼容自查：
-- 1) 普通 DDL，无 CTE/窗口函数/JSON_TABLE/IF( 函数；条件用 CASE WHEN。
-- 2) 生成列不写可空声明（MySQL 1064），COLLATE 显式写在类型后；被拼进的列均 NOT NULL。
-- 3) 先建新键再删旧键：idx_jp_campus_code / idx_jp_classroom_code 同时承担
--    fk_jp_campus_institution、fk_jp_classroom_campus 的最左前缀索引与预检查询的查找索引，
--    先建后删避免 1553；新旧键不同名，不会 1061。
-- 4) 本地 MySQL 9.7.2 已实测；生产 8.4 待验（生成列 8.0 起支持，写法与 V72/V103 同源）。

ALTER TABLE `jp_campus`
    ADD COLUMN `campus_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `campus_code`) END
        ) STORED COMMENT '校区编码防重键(未删除时取 机构ID#校区编码,软删墓碑为 NULL)',
    ADD COLUMN `campus_name_guard` VARCHAR(160) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `campus_name`) END
        ) STORED COMMENT '校区名称防重键(未删除时取 机构ID#校区名称,软删墓碑为 NULL)',
    ADD KEY `idx_jp_campus_code` (`institution_id`, `campus_code`),
    ADD KEY `idx_jp_campus_name` (`institution_id`, `campus_name`),
    ADD UNIQUE KEY `uk_jp_campus_code_guard` (`campus_code_guard`),
    ADD UNIQUE KEY `uk_jp_campus_name_guard` (`campus_name_guard`);

ALTER TABLE `jp_campus`
    DROP INDEX `uk_jp_campus_code`,
    DROP INDEX `uk_jp_campus_name`;

ALTER TABLE `jp_classroom`
    ADD COLUMN `classroom_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `campus_id`, `classroom_code`) END
        ) STORED COMMENT '教室编码防重键(未删除时取 校区ID#教室编码,软删墓碑为 NULL)',
    ADD COLUMN `classroom_name_guard` VARCHAR(160) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `campus_id`, `classroom_name`) END
        ) STORED COMMENT '教室名称防重键(未删除时取 校区ID#教室名称,软删墓碑为 NULL)',
    ADD KEY `idx_jp_classroom_code` (`campus_id`, `classroom_code`),
    ADD KEY `idx_jp_classroom_name` (`campus_id`, `classroom_name`),
    ADD UNIQUE KEY `uk_jp_classroom_code_guard` (`classroom_code_guard`),
    ADD UNIQUE KEY `uk_jp_classroom_name_guard` (`classroom_name_guard`);

ALTER TABLE `jp_classroom`
    DROP INDEX `uk_jp_classroom_code`,
    DROP INDEX `uk_jp_classroom_name`;
