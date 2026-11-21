-- V106: 教师/学员家长的编码与手机号唯一约束改为「只约束未删除行」。
--
-- 与 V103~V105 同一配方，但这两张表有可空业务键，守卫表达式必须单独处理 NULL：
--   · jp_teacher.phone        可空
--   · jp_student_parent.parent_phone 可空
-- 现状语义是「NULL 不参与唯一比对，允许多条 NULL 手机号」。守卫列只在 deleted = 0 且业务键
-- IS NOT NULL 时取值，其余一律 NULL —— 既让墓碑腾出业务键，又不改变「多条空手机号可共存」。
--
-- 不动的键：uk_jp_teacher_user_id（user_id 可空，本身就是「一个账号只能绑一个教师」的约束，
-- 与软删复用无关；解绑逻辑另行处理）。
--
-- 不走物理删除：jp_teacher 被 jp_class / jp_schedule_plan / schedule_lesson 等外键引用；
-- jp_student_parent 有 fk_jp_student_parent_student 指向 jp_student，软删行同样占着外键（1451）。
--
-- 兼容自查：
-- 1) 普通 DDL，无 CTE/窗口函数/JSON_TABLE/IF( 函数；条件用 CASE WHEN。
-- 2) 生成列不写可空声明（MySQL 1064），COLLATE 显式写在类型后；列长按 范围ID + '#' + 业务键
--    估算，96 足够（teacher_code/phone/parent_phone 最长 64/32/32 字节）。
-- 3) 先建新键再删旧键：idx_jp_teacher_code 承担 fk_jp_teacher_institution 的最左前缀索引，
--    idx_jp_student_parent_phone 承担预检查找；student_id 外键另有 idx_jp_student_parent_primary
--    兜底。新旧键不同名，不会 1061、1553。
-- 4) 本地 MySQL 9.7.2 已实测；生产 8.4 待验（生成列 8.0 起支持，写法与 V72/V103 同源）。

ALTER TABLE `jp_teacher`
    ADD COLUMN `teacher_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `teacher_code`) END
        ) STORED COMMENT '教师编码防重键(未删除时取 机构ID#教师编码,软删墓碑为 NULL)',
    ADD COLUMN `teacher_phone_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 AND `phone` IS NOT NULL
                THEN CONCAT_WS('#', `institution_id`, `phone`) END
        ) STORED COMMENT '教师手机号防重键(未删除且手机号非空时取值,软删墓碑与空手机号为 NULL)',
    ADD KEY `idx_jp_teacher_code` (`institution_id`, `teacher_code`),
    ADD KEY `idx_jp_teacher_phone` (`institution_id`, `phone`),
    ADD UNIQUE KEY `uk_jp_teacher_code_guard` (`teacher_code_guard`),
    ADD UNIQUE KEY `uk_jp_teacher_phone_guard` (`teacher_phone_guard`);

ALTER TABLE `jp_teacher`
    DROP INDEX `uk_jp_teacher_code`,
    DROP INDEX `uk_jp_teacher_phone`;

ALTER TABLE `jp_student_parent`
    ADD COLUMN `parent_phone_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 AND `parent_phone` IS NOT NULL
                THEN CONCAT_WS('#', `student_id`, `parent_phone`) END
        ) STORED COMMENT '家长手机号防重键(未删除且手机号非空时取值,软删墓碑与空手机号为 NULL)',
    ADD KEY `idx_jp_student_parent_phone` (`student_id`, `parent_phone`),
    ADD UNIQUE KEY `uk_jp_student_parent_phone_guard` (`parent_phone_guard`);

ALTER TABLE `jp_student_parent`
    DROP INDEX `uk_jp_student_parent_phone`;
