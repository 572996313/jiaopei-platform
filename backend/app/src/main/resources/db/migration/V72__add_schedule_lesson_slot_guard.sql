-- 课次「班级 + 日期 + 时段」「教室 + 日期 + 时段」防重兜底。
-- 应用层的冲突校验是先查后插（ScheduleLessonServiceImpl#detectConflicts、SchedulePlanServiceImpl#assertCellsFree），
-- 双页签或直调接口能落进两条完全重复的占用课次，这里按 AGENTS 安全约定第 4 条补数据库唯一约束。
-- 键做成生成列、不把 deleted 直接写进唯一键：软删行与已停课行都不该占住这个格子。
-- 本地实测依据：现网 63 行里有 27 行属于软删/已停课，其中同格子最多 4 条软删行与现役行重叠，
-- 含 deleted 的唯一键会在这些行上直接撞 1062；非占用态取 NULL，NULL 不参与唯一比对。
-- 生成列不能写成 `VARCHAR(64) NULL GENERATED ...`（MySQL 1064），可空声明留空即默认。
ALTER TABLE `schedule_lesson`
    ADD COLUMN `class_slot_guard` VARCHAR(64) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 AND `lesson_status` IN ('scheduled', 'rescheduled', 'makeup')
                THEN CONCAT_WS('#', `class_id`, `lesson_date`, `time_slot_id`)
            END
        ) STORED COMMENT '班级时段占用防重键(未删除且状态为已排课/已调课/已补课时取 班级#日期#时段,其余为 NULL)',
    ADD COLUMN `classroom_slot_guard` VARCHAR(64) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 AND `lesson_status` IN ('scheduled', 'rescheduled', 'makeup')
                THEN CONCAT_WS('#', `classroom_id`, `lesson_date`, `time_slot_id`)
            END
        ) STORED COMMENT '教室时段占用防重键(未删除且状态为已排课/已调课/已补课时取 教室#日期#时段,其余为 NULL)',
    ADD UNIQUE KEY `uk_schedule_lesson_class_slot` (`class_slot_guard`),
    ADD UNIQUE KEY `uk_schedule_lesson_classroom_slot` (`classroom_slot_guard`);
