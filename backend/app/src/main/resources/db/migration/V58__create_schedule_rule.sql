-- 排课规则：班级或教师在"星期×节次"网格上的禁排/必排约束（阶段B）
-- 格子键与 jp_schedule_plan.slots_json 同构（{weekday,timeSlotId}）：jp_time_slot 挂在机构+校区、不随学期重建，
-- 所以按时间段ID绑定不会跨学期失效；时段被删除时规则随之失效，与计划同一失败面，不另设兜底。
-- 不建唯一键：同一班级/教师允许多条规则（不同格子、不同生效区间），去重靠应用层按 (对象,类型,格子) 提示。

CREATE TABLE jp_schedule_rule (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离)',
    rule_type VARCHAR(16) NOT NULL COMMENT '规则类型(forbidden=禁排,required=必排)',
    target_type VARCHAR(16) NOT NULL COMMENT '规则对象类型(class=班级,teacher=教师)',
    target_id BIGINT NOT NULL COMMENT '规则对象ID(target_type=class时为班级ID,=teacher时为教师ID)',
    slots_json VARCHAR(1024) NOT NULL COMMENT '每周节次格子(JSON数组[{weekday,timeSlotId}],weekday 1=周一,7=周日)',
    start_date DATE NULL COMMENT '生效起始日期(空=不限,按课次日期判定是否在区间内)',
    end_date DATE NULL COMMENT '生效截止日期(空=不限)',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '规则状态(active=启用,inactive=停用;停用不参与校验)',
    remark VARCHAR(500) NULL COMMENT '批注(为什么禁排/必排,校验提示里带出)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    KEY idx_jp_schedule_rule_target (institution_id, target_type, target_id, deleted),
    CONSTRAINT fk_jp_schedule_rule_institution FOREIGN KEY (institution_id) REFERENCES sys_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课规则(班级/教师的星期×节次禁排必排约束)';
