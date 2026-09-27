-- 校区日历（停排日）：把"国庆放假、校区检修、期中停课"这类整日不排课的日子登记成数据，
-- 而不是每次排课时靠操作员自己记。排课计划预览与手工建课次都会读出落在校区内的停排日并提示。
--
-- 只提示不拦（与教师超课时、学员跨班撞课同一档）：放假安排常临时调整，硬拦会把"就要在这天上两节"
-- 的合理操作也堵死，而且计划已经生成到一半时被拦下更难收拾。判据是——这个信息错了，操作员自己
-- 能改过来；系统替他拦，改错了反而没人知道。
--
-- campus_id = 0 表示机构级（全校区停排）：
--   用 0 不用 NULL，因为唯一键里的 NULL 互不相等，同一天能插进无数条机构级记录；
--   同理**不给 campus_id 建外键**（0 这一行在 jp_campus 里不存在，加了外键第一句插入就失败）。
--   读取侧按 `campus_id IN (0, 本校区的 id)` 取，别的校区的停排日不影响本校区。
--
-- 删除走物理删除（见 SchoolCalendarDayMapper#deletePhysicallyById）：deleted 是 0/1 二值，
-- 逻辑删除会让"今年登记过、撤销、明年同一天再登记"撞在墓碑上，与 AGENTS 安全约定第 3 条同口径。

CREATE TABLE jp_school_calendar_day (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离)',
    campus_id BIGINT NOT NULL DEFAULT 0 COMMENT '校区ID(0=机构级,全校区停排;不建外键的缘由见脚本头部)',
    day_date DATE NOT NULL COMMENT '停排日期',
    day_name VARCHAR(64) NOT NULL COMMENT '名称(如 国庆节/校区检修,排课提示语里带出)',
    remark VARCHAR(500) NULL COMMENT '备注(为什么停排,提示语里带出)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_school_calendar_day (institution_id, campus_id, day_date, deleted),
    KEY idx_jp_school_calendar_day_date (institution_id, day_date, deleted),
    CONSTRAINT fk_jp_school_calendar_day_institution FOREIGN KEY (institution_id) REFERENCES sys_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校区日历停排日(机构级用 campus_id=0,排课只提示不拦)';
