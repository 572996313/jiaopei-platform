-- V38: 时段模板与时间段表结构修复(生产 MySQL 5.7 兼容版)
--
-- 1) 清理 jp_time_slot 存量软删记录,并按新唯一键形状对未删行去重(V18 同款思路);
-- 2) 重建时间段唯一键为 机构+校区+编码(名称)+星期+deleted:
--    旧 uk 不含 campus_id/weekday/deleted,不同校区同编码、模板按星期展开的同节次、
--    逻辑删除后重建同名时间段都会撞键;
-- 3) 新增时段模板表 jp_time_slot_template,config_json 存节次参数快照;
--    网格编辑器按模板批量展开生成 jp_time_slot 行,jp_time_slot.template_id 记录来源。
--
-- 生产兼容约束(改迁移前必读,违反会在 Flyway 解析/执行阶段直接失败):
-- - 生产库为 MySQL 5.7,Flyway 走 MySQL-5 解析器:禁止 CTE(WITH)、窗口函数(ROW_NUMBER/OVER)、
--   表达式中调用 IF( 函数(解析器会按存储程序 IF 块处理而报语法错);判存统一用 CASE WHEN。
-- - MySQL(任何版本)均不支持 ALTER 内的 ADD/DROP COLUMN|INDEX|KEY IF [NOT] EXISTS,那是 MariaDB 方言;
--   存在性守卫一律走 information_schema + PREPARE 动态执行(仅 CREATE TABLE IF NOT EXISTS 合法)。
-- - 本迁移可能停在半实施状态后重跑,所有 DDL 必须幂等;建唯一键前必须先清理撞键存量行。

-- 0) 软删记录先行物理删除,释放唯一键占用
DELETE FROM jp_time_slot WHERE deleted = 1;

-- 1) 存量未删行按新唯一键形状去重,同组仅保留最小 id(与"先查后插+唯一约束"约定配套的建键前置清理);
--    campus_id 为 NULL 的行不参与:MySQL 唯一索引不约束 NULL,与新 uk 语义保持一致
DELETE t1 FROM jp_time_slot t1
    JOIN jp_time_slot t2
      ON t1.id > t2.id
     AND t1.deleted = t2.deleted
     AND t1.institution_id = t2.institution_id
     AND t1.campus_id = t2.campus_id
     AND t1.slot_code = t2.slot_code
     AND t1.weekday = t2.weekday;

DELETE t1 FROM jp_time_slot t1
    JOIN jp_time_slot t2
      ON t1.id > t2.id
     AND t1.deleted = t2.deleted
     AND t1.institution_id = t2.institution_id
     AND t1.campus_id = t2.campus_id
     AND t1.slot_name = t2.slot_name
     AND t1.weekday = t2.weekday;

-- 2) 新增来源模板列(半实施重跑时列可能已存在,判存后再加)
SET @sql = (SELECT CASE WHEN COUNT(*) = 0
    THEN 'ALTER TABLE jp_time_slot ADD COLUMN template_id BIGINT NULL COMMENT ''来源时段模板ID,空表示手工录入'' AFTER status'
    ELSE 'SELECT 1' END
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jp_time_slot' AND COLUMN_NAME = 'template_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT CASE WHEN COUNT(*) = 0
    THEN 'ALTER TABLE jp_time_slot ADD KEY idx_jp_time_slot_template (template_id)'
    ELSE 'SELECT 1' END
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jp_time_slot' AND INDEX_NAME = 'idx_jp_time_slot_template');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 时段类型注释与表注释(MODIFY 幂等,重复执行无副作用)
ALTER TABLE jp_time_slot
    MODIFY COLUMN `slot_type` varchar(32) NOT NULL DEFAULT 'teaching' COMMENT '时段类型(morning_study=早自习,teaching=授课,break=课间休息,self_study=晚自习)',
    COMMENT = '时间段(每星期每节次一行,由时段模板批量展开或手工录入)';

-- 4) 同名旧唯一键若列形状不符,会让第 5 步 ADD 报 Duplicate key name,先删同名且形状不符的;
--    其他名字的旧键(如 V2 的 uk_jp_time_slot_org_code)留到新键建好之后再删
SET @drop_same = (SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', t.idx) SEPARATOR ', ')
    FROM (SELECT INDEX_NAME AS idx, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jp_time_slot' AND NON_UNIQUE = 0
          AND INDEX_NAME IN ('uk_jp_time_slot_code', 'uk_jp_time_slot_name')
        GROUP BY INDEX_NAME) t
    WHERE NOT (
        (t.idx = 'uk_jp_time_slot_code' AND t.cols = 'institution_id,campus_id,slot_code,weekday,deleted')
        OR (t.idx = 'uk_jp_time_slot_name' AND t.cols = 'institution_id,campus_id,slot_name,weekday,deleted')
    ));
SET @sql = COALESCE(CONCAT('ALTER TABLE jp_time_slot ', @drop_same), 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 按目标形状重建两个唯一键(已是目标形状则跳过)
SET @sql = (SELECT CASE WHEN COUNT(*) = 0
    THEN 'ALTER TABLE jp_time_slot ADD UNIQUE KEY uk_jp_time_slot_code (institution_id, campus_id, slot_code, weekday, deleted)'
    ELSE 'SELECT 1' END
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jp_time_slot' AND INDEX_NAME = 'uk_jp_time_slot_code');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT CASE WHEN COUNT(*) = 0
    THEN 'ALTER TABLE jp_time_slot ADD UNIQUE KEY uk_jp_time_slot_name (institution_id, campus_id, slot_name, weekday, deleted)'
    ELSE 'SELECT 1' END
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jp_time_slot' AND INDEX_NAME = 'uk_jp_time_slot_name');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6) 清理其余形状不符的历史遗留唯一键。此时目标 uk 已含 institution_id 前缀,
--    institution 外键始终有可用前缀索引,删除不会触发 1553 "needed in a foreign key constraint"
SET @drop_legacy = (SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', t.idx) SEPARATOR ', ')
    FROM (SELECT INDEX_NAME AS idx
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jp_time_slot' AND NON_UNIQUE = 0
          AND INDEX_NAME NOT IN ('PRIMARY', 'uk_jp_time_slot_code', 'uk_jp_time_slot_name')
        GROUP BY INDEX_NAME) t);
SET @sql = COALESCE(CONCAT('ALTER TABLE jp_time_slot ', @drop_legacy), 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7) 时段模板表(半实施重跑时可能已建好,IF NOT EXISTS 后补注释)
CREATE TABLE IF NOT EXISTS jp_time_slot_template (
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

ALTER TABLE jp_time_slot_template
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NULL COMMENT '校区ID 空表示机构级模板',
    MODIFY COLUMN `template_name` varchar(128) NOT NULL COMMENT '模板名称',
    MODIFY COLUMN `config_json` json NOT NULL COMMENT '节次参数快照(课程时长/课间/各时段节数/大课间/套用星期)',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,inactive=停用)',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
    MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    COMMENT = '时段模板(节次参数快照,套用时展开为jp_time_slot行)';
