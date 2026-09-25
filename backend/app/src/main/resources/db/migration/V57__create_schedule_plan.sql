-- 排课计划：班级"每周固定节次模式"的批量展开来源（批量周期排课一期）
-- 课次幂等靠应用层按 (plan_id, lesson_date, time_slot_id) 查重，不建数据库唯一键：
-- 计划的"整批撤未上课次"是软删，同键撤掉后重排再撤会与上一份 deleted=1 的行撞唯一键（deleted 进 uk 只容一份已删行）。

CREATE TABLE jp_schedule_plan (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离)',
    campus_id BIGINT NOT NULL COMMENT '校区ID',
    term_id BIGINT NOT NULL COMMENT '学期ID',
    class_id BIGINT NOT NULL COMMENT '班级ID(课程随班级带出,不在计划单独存)',
    classroom_id BIGINT NOT NULL COMMENT '教室ID(一期固定教室,不做自动选室)',
    teacher_ids_json VARCHAR(512) NOT NULL COMMENT '授课教师ID列表(JSON数组,展开课次时写入课次教师关系)',
    slots_json VARCHAR(1024) NOT NULL COMMENT '每周节次模式(JSON数组[{weekday,timeSlotId}],weekday 1=周一,7=周日)',
    week_frequency VARCHAR(16) NOT NULL DEFAULT 'every_week' COMMENT '排课频率(every_week=每周,odd_week=单周,even_week=双周;单双周以起始日期所在自然周为第1周)',
    start_date DATE NOT NULL COMMENT '生效起始日期',
    end_date DATE NOT NULL COMMENT '生效截止日期',
    skip_dates_json VARCHAR(2048) NULL COMMENT '跳过日期列表(JSON数组,ISO日期字符串,展开时整日略过)',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '计划状态(active=启用,inactive=停用;停用仅停止展开,不影响已生成课次)',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    KEY idx_jp_schedule_plan_class (class_id, deleted),
    KEY idx_jp_schedule_plan_institution (institution_id, campus_id, deleted),
    CONSTRAINT fk_jp_schedule_plan_institution FOREIGN KEY (institution_id) REFERENCES sys_institution (id),
    CONSTRAINT fk_jp_schedule_plan_campus FOREIGN KEY (campus_id) REFERENCES jp_campus (id),
    CONSTRAINT fk_jp_schedule_plan_term FOREIGN KEY (term_id) REFERENCES jp_term (id),
    CONSTRAINT fk_jp_schedule_plan_class FOREIGN KEY (class_id) REFERENCES jp_class (id),
    CONSTRAINT fk_jp_schedule_plan_classroom FOREIGN KEY (classroom_id) REFERENCES jp_classroom (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课计划(每周节次模式,批量展开为课次批次)';

ALTER TABLE schedule_lesson
    ADD COLUMN plan_id BIGINT NULL COMMENT '来源排课计划ID(手工排课为空)' AFTER time_slot_id,
    ADD KEY idx_schedule_lesson_plan (plan_id, lesson_date, time_slot_id);
