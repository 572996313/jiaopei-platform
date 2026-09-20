-- V38: 新增时段模板表,调整时间段唯一键
-- 旧编码键不含校区/星期、名称键不含星期:模板按星期展开同一节次(如 AM1 周一/周二各一条)会撞键,
-- 且不含 deleted 无法删后重建。新键为 机构+校区+编码(名称)+星期+未删除。

-- 1) 清掉软删数据，释放唯一键占用
DELETE FROM jp_time_slot WHERE deleted = 1;

-- 2) 时间段表加来源模板列
ALTER TABLE jp_time_slot
    ADD COLUMN template_id BIGINT NULL COMMENT '来源时段模板ID,空表示手工录入'
        AFTER status;

CREATE INDEX idx_jp_time_slot_template
ON jp_time_slot (template_id);

ALTER TABLE jp_time_slot
    COMMENT = '时间段(每星期每节次一行,由时段模板批量展开或手工录入)';

-- 3) 重建编码唯一键:先建新键(以 institution_id 开头,后面删旧键时外键前缀索引不断),再删旧键
CREATE UNIQUE INDEX uk_jp_time_slot_code
ON jp_time_slot (
    institution_id,
    campus_id,
    slot_code,
    weekday,
    deleted
);

ALTER TABLE jp_time_slot
    DROP INDEX uk_jp_time_slot_org_code;

-- 4) 重建名称唯一键:新旧键同名,先删旧再建新
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
CREATE TABLE jp_time_slot_template (
    id BIGINT NOT NULL COMMENT '主键ID',
    institution_id BIGINT NOT NULL COMMENT '机构ID',
    campus_id BIGINT NULL COMMENT '校区ID 空表示机构级模板',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    config_json JSON NOT NULL COMMENT '节次参数快照(课程时长/课间/各时段节数/大课间/套用星期)',
    status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,inactive=停用)',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_time_slot_template_name (institution_id, campus_id, template_name, deleted),
    KEY idx_jp_time_slot_template_institution (institution_id, campus_id),
    CONSTRAINT fk_jp_time_slot_template_institution FOREIGN KEY (institution_id) REFERENCES jp_institution (id),
    CONSTRAINT fk_jp_time_slot_template_campus FOREIGN KEY (campus_id) REFERENCES jp_campus (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='时段模板(节次参数快照,套用时展开为jp_time_slot行)';

-- 若执行到一半失败:用 SHOW INDEX FROM jp_time_slot / SHOW COLUMNS FROM jp_time_slot LIKE 'template_id'
-- 查已执行到哪步,注释掉已执行语句后重跑;最后删除失败记录
-- DELETE FROM flyway_schema_history WHERE version = '38' AND success = 0; 再重启后端续跑。
