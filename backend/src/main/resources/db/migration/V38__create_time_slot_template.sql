-- V38: 新增时段模板表,调整时间段唯一键
--
-- 唯一键改为 机构+校区+编码(名称)+星期+未删除:旧键不含 weekday,
-- 模板按星期展开的同一节次(如 AM1 周一/周二各一条)会撞键;含 deleted 才能删后重建。
-- 生产库为 MySQL 5.x,本脚本只用普通 DDL;若上次执行到一半失败,先按文末指引把已执行的语句删掉再跑剩余的。

-- 1) 清掉软删数据,释放唯一键占用
DELETE FROM jp_time_slot WHERE deleted = 1;

-- 2) 时间段表加来源模板列
ALTER TABLE jp_time_slot
    ADD COLUMN template_id BIGINT NULL COMMENT '来源时段模板ID,空表示手工录入' AFTER status,
    ADD KEY idx_jp_time_slot_template (template_id),
    COMMENT = '时间段(每星期每节次一行,由时段模板批量展开或手工录入)';

-- 3) 重建编码唯一键(删旧建新放同一条语句,institution 外键始终有前缀索引可用)
ALTER TABLE jp_time_slot
    DROP INDEX uk_jp_time_slot_org_code,
    ADD UNIQUE KEY uk_jp_time_slot_code (institution_id, campus_id, slot_code, weekday, deleted);

-- 4) 重建名称唯一键(同名替换)
ALTER TABLE jp_time_slot
    DROP INDEX uk_jp_time_slot_name,
    ADD UNIQUE KEY uk_jp_time_slot_name (institution_id, campus_id, slot_name, weekday, deleted);

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时段模板(节次参数快照,套用时展开为jp_time_slot行)';

-- ------------------------------------------------------------------
-- 半实施恢复指引(先跑这三句看现状,再对应处理):
--   SHOW COLUMNS FROM jp_time_slot LIKE 'template_id';
--   SHOW INDEX FROM jp_time_slot;
--   SHOW CREATE TABLE jp_time_slot_template;  /  SHOW TABLES LIKE 'jp_time_slot_template';
-- 第 2 步已执行过(报 Duplicate column) -> 删掉第 2 步;
-- 第 3/4 步:新键已在 -> 删掉该步;旧键已删但新键没建成(报 Can't DROP ... 1091) ->
--   把该步里 DROP INDEX 一行去掉,只留 ADD UNIQUE KEY;
-- 模板表已建 -> 删掉第 5 步。若存量行撞新唯一键(ADD UNIQUE KEY 报 Duplicate entry),
-- 同组只保留最小 id 后重试:
--   DELETE t1 FROM jp_time_slot t1 JOIN jp_time_slot t2
--     ON t1.id > t2.id AND t1.institution_id = t2.institution_id
--    AND t1.campus_id = t2.campus_id AND t1.slot_code = t2.slot_code AND t1.weekday = t2.weekday;
-- 若 SHOW CREATE TABLE jp_time_slot 里 institution 外键已不在(被旧脚本连带删了),补回:
--   ALTER TABLE jp_time_slot ADD CONSTRAINT fk_jp_time_slot_institution FOREIGN KEY (institution_id) REFERENCES jp_institution (id);
-- 处理完删除 flyway_schema_history 中 version='38' 的失败记录,重启后端续跑。
-- ------------------------------------------------------------------
