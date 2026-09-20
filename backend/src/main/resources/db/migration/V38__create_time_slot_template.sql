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

-- 半实施恢复(重放报 1060 列已存在 / 1061 键同名 / 1091 待删键不存在时,说明上次已执行过部分语句;
-- Flyway 每次重启都会从头重放整个脚本,注释掉已执行语句是无效的,正确做法):
-- 1) 查现状: SHOW COLUMNS FROM jp_time_slot LIKE 'template_id';  SHOW INDEX FROM jp_time_slot;
--    SHOW TABLES LIKE 'jp_time_slot_template';  把本脚本当"待执行清单",缺哪条就在生产库手工执行哪条
--    (ADD UNIQUE KEY 若报 1062 撞存量数据,先按第 1 步 DELETE 软删行、再自连接去重:
--     DELETE t1 FROM jp_time_slot t1 JOIN jp_time_slot t2 ON t1.id > t2.id
--       AND t1.institution_id = t2.institution_id AND t1.campus_id = t2.campus_id
--       AND t1.slot_code = t2.slot_code AND t1.weekday = t2.weekday);
-- 2) 全部补齐后,删除失败记录并手工补插一条成功记录(Flyway 从此认为 V38 已应用,不再重放):
--    DELETE FROM flyway_schema_history WHERE version = '38' AND success = 0;
--    INSERT INTO flyway_schema_history
--      (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
--    VALUES ((SELECT MAX(installed_rank) + 1 FROM flyway_schema_history t), '38', 'create time slot template', 'SQL',
--            'V38__create_time_slot_template.sql', <与部署jar内脚本一致的checksum>, 'root', NOW(), 0, 1);
-- 3) 重启后端,日志应出现 "Schema `jiaopei_platform` is up to date. No migration necessary."
