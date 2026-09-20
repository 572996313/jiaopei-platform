-- 1) 清掉软删数据，释放唯一键占用
DELETE FROM jp_time_slot WHERE deleted = 1;

-- 2) 时间段表加来源模板列
ALTER TABLE jp_time_slot
    ADD COLUMN IF NOT EXISTS template_id BIGINT NULL COMMENT '来源时段模板ID,空表示手工录入'
        AFTER status;

CREATE INDEX idx_jp_time_slot_template
ON jp_time_slot (template_id);

ALTER TABLE jp_time_slot
    COMMENT = '时间段(每星期每节次一行,由时段模板批量展开或手工录入)';

-- 3) 重建编码唯一键

CREATE UNIQUE INDEX uk_jp_time_slot_code
ON jp_time_slot (
    institution_id,
    campus_id,
    slot_code,
    weekday,
    deleted
);

-- 4) 重建名称唯一键
ALTER TABLE jp_time_slot
    DROP INDEX uk_jp_time_slot_name;

ALTER TABLE jp_time_slot
ADD UNIQUE KEY uk_jp_time_slot_name (
    institution_id,
    campus_id,
    slot_name,
    weekday,
    deleted
);


-- 5) 时段模板表
CREATE TABLE IF NOT EXISTS jp_time_slot_template (
    id BIGINT NOT NULL COMMENT '主键ID',
    institution_id BIGINT NOT NULL COMMENT '机构ID',
    campus_id BIGINT NULL COMMENT '校区ID 空表示机构级模板',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    config_json JSON NOT NULL COMMENT '节次参数快照',
    status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_time_slot_template_name (institution_id, campus_id, template_name, deleted),
    KEY idx_jp_time_slot_template_institution (institution_id, campus_id),
    CONSTRAINT fk_jp_time_slot_template_institution FOREIGN KEY (institution_id) REFERENCES jp_institution (id),
    CONSTRAINT fk_jp_time_slot_template_campus FOREIGN KEY (campus_id) REFERENCES jp_campus (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='时段模板(节次参数快照,套用时展开为jp_time_slot行)';