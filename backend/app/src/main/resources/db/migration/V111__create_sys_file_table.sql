-- ============================================================================
-- 文件管理：sys_file 元数据表。
--
-- 双轨：本文件是教培侧真正执行的那份；底座基线侧内容一字不差，见
--   backend/cornerstone-core/src/main/resources/cornerstone/migration/V115__create_sys_file_table.sql
-- 教培库只跑 app 侧 V1xx，下游从零建库只跑底座 V1xx，两份都要有。
--
-- 设计口径：
--   1. 业务表只存 file_id，不存 object_key / bucket / URL；
--   2. object_key 由后端生成（机构ID/年/月/UUID.扩展名），不含用户输入；
--   3. 删除走物理删除（FileMapper#physicalDeleteById），不留墓碑；唯一键仍按底座约定带
--      deleted 列（CornerstoneBaselineGuardTest 硬性要求，物理删除下 deleted 恒为 0，无额外代价）。
-- ============================================================================

CREATE TABLE `sys_file` (
    `id`             BIGINT       NOT NULL                      COMMENT '主键ID',
    `institution_id` BIGINT       NOT NULL                      COMMENT '所属机构ID',
    `bucket`         VARCHAR(64)  NOT NULL                      COMMENT '对象存储桶名',
    `object_key`     VARCHAR(512) NOT NULL                      COMMENT '对象键(后端生成:机构ID/年/月/UUID.扩展名)',
    `original_name`  VARCHAR(255) NOT NULL                      COMMENT '原始文件名',
    `extension`      VARCHAR(16)  DEFAULT NULL                  COMMENT '扩展名(小写,不含点)',
    `content_type`   VARCHAR(128) DEFAULT NULL                  COMMENT 'MIME类型',
    `size_bytes`     BIGINT       NOT NULL                      COMMENT '文件字节数',
    `sha256`         CHAR(64)     DEFAULT NULL                  COMMENT '内容摘要(SHA-256,预留秒传/去重)',
    `biz_type`       VARCHAR(64)  DEFAULT NULL                  COMMENT '业务类型码(调用方约定,如student_photo)',
    `biz_id`         BIGINT       DEFAULT NULL                  COMMENT '业务主键(为空视为未绑定,按孤儿清理)',
    `uploaded_by`    BIGINT       DEFAULT NULL                  COMMENT '上传人ID',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by`     BIGINT       DEFAULT NULL                  COMMENT '创建人ID',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `updated_by`     BIGINT       DEFAULT NULL                  COMMENT '更新人ID',
    `deleted`        TINYINT(1)   NOT NULL DEFAULT 0            COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_file_object_key` (`object_key`, `deleted`),
    KEY `idx_sys_file_inst_biz` (`institution_id`, `biz_type`, `biz_id`),
    KEY `idx_sys_file_inst_created` (`institution_id`, `created_at`),
    KEY `idx_sys_file_orphan` (`biz_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据';
