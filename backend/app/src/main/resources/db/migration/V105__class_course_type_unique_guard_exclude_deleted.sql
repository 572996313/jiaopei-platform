-- V105: 班级/课程/课程分类/班型的唯一约束改为「只约束未删除行」（与 V103/V104 同一配方）。
--
-- 起因：这四张表的 uk_*_code / uk_*_name 都不含 deleted。删除走 @TableLogic 逻辑删除，墓碑仍
-- 占着编码与名称；应用层预检走 MyBatis-Plus 会自动补 deleted = 0、查不到墓碑，现场看到的就是
-- 「库里删了还提示已存在」。
--
-- 守卫列：未删除行取「范围ID#业务键」，墓碑取 NULL（NULL 不参与唯一比对，可容纳多条墓碑）。
-- 四张表的键列均为 NOT NULL，已核对（information_schema），无需 NULL 特判。
--
-- 不走物理删除：这四张表都被大量子表外键引用（班级被 8 张表引用），软删子行同样占着外键（1451）。
--
-- 兼容自查：
-- 1) 普通 DDL，无 CTE/窗口函数/JSON_TABLE/IF( 函数；条件用 CASE WHEN。
-- 2) 生成列不写可空声明（MySQL 1064），COLLATE 显式写在类型后。
-- 3) 先建新键再删旧键：新复合普通索引同时承担各表 FK 的最左前缀索引与预检查找索引，
--    避免 1553；新旧键不同名，不会 1061。
-- 4) 本地 MySQL 9.7.2 已实测；生产 8.4 待验（生成列 8.0 起支持，写法与 V72/V103 同源）。

ALTER TABLE `jp_class`
    ADD COLUMN `class_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `campus_id`, `class_code`) END
        ) STORED COMMENT '班级编码防重键(未删除时取 校区ID#班级编码,软删墓碑为 NULL)',
    ADD COLUMN `class_name_guard` VARCHAR(160) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `campus_id`, `class_name`) END
        ) STORED COMMENT '班级名称防重键(未删除时取 校区ID#班级名称,软删墓碑为 NULL)',
    ADD KEY `idx_jp_class_code` (`campus_id`, `class_code`),
    ADD KEY `idx_jp_class_name` (`campus_id`, `class_name`),
    ADD UNIQUE KEY `uk_jp_class_code_guard` (`class_code_guard`),
    ADD UNIQUE KEY `uk_jp_class_name_guard` (`class_name_guard`);

ALTER TABLE `jp_class`
    DROP INDEX `uk_jp_class_code`,
    DROP INDEX `uk_jp_class_name`;

ALTER TABLE `jp_course`
    ADD COLUMN `course_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `course_code`) END
        ) STORED COMMENT '课程编码防重键(未删除时取 机构ID#课程编码,软删墓碑为 NULL)',
    ADD COLUMN `course_name_guard` VARCHAR(160) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `course_name`) END
        ) STORED COMMENT '课程名称防重键(未删除时取 机构ID#课程名称,软删墓碑为 NULL)',
    ADD KEY `idx_jp_course_code` (`institution_id`, `course_code`),
    ADD KEY `idx_jp_course_name` (`institution_id`, `course_name`),
    ADD UNIQUE KEY `uk_jp_course_code_guard` (`course_code_guard`),
    ADD UNIQUE KEY `uk_jp_course_name_guard` (`course_name_guard`);

ALTER TABLE `jp_course`
    DROP INDEX `uk_jp_course_code`,
    DROP INDEX `uk_jp_course_name`;

ALTER TABLE `jp_course_category`
    ADD COLUMN `category_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `category_code`) END
        ) STORED COMMENT '课程分类编码防重键(未删除时取 机构ID#分类编码,软删墓碑为 NULL)',
    ADD COLUMN `category_name_guard` VARCHAR(160) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `category_name`) END
        ) STORED COMMENT '课程分类名称防重键(未删除时取 机构ID#分类名称,软删墓碑为 NULL)',
    ADD KEY `idx_jp_course_category_code` (`institution_id`, `category_code`),
    ADD KEY `idx_jp_course_category_name` (`institution_id`, `category_name`),
    ADD UNIQUE KEY `uk_jp_course_category_code_guard` (`category_code_guard`),
    ADD UNIQUE KEY `uk_jp_course_category_name_guard` (`category_name_guard`);

ALTER TABLE `jp_course_category`
    DROP INDEX `uk_jp_course_category_code`,
    DROP INDEX `uk_jp_course_category_name`;

ALTER TABLE `jp_class_type`
    ADD COLUMN `type_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `type_code`) END
        ) STORED COMMENT '班型编码防重键(未删除时取 机构ID#班型编码,软删墓碑为 NULL)',
    ADD COLUMN `type_name_guard` VARCHAR(160) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `type_name`) END
        ) STORED COMMENT '班型名称防重键(未删除时取 机构ID#班型名称,软删墓碑为 NULL)',
    ADD KEY `idx_jp_class_type_code` (`institution_id`, `type_code`),
    ADD KEY `idx_jp_class_type_name` (`institution_id`, `type_name`),
    ADD UNIQUE KEY `uk_jp_class_type_code_guard` (`type_code_guard`),
    ADD UNIQUE KEY `uk_jp_class_type_name_guard` (`type_name_guard`);

ALTER TABLE `jp_class_type`
    DROP INDEX `uk_jp_class_type_code`,
    DROP INDEX `uk_jp_class_type_name`;
