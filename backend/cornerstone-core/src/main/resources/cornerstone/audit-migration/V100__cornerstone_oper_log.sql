-- ============================================================================
-- V100：Cornerstone 底座基线（日志库结构）
--
-- 归属：cornerstone-core jar，目录 classpath:cornerstone/audit-migration，
--       由 auditFlyway（第二 Flyway 实例）执行，与业务库的 primaryFlyway 完全分开。
--       新项目把 cornerstone.flyway.audit-locations 配成这个目录即可获得本表；
--       教培仓库不走这条路（它的日志表在 app/src/main/resources/db/audit-migration/V1，
--       audit-locations 保持默认值）。
--
-- 已知不一致（问题记录 #7，未收口）：底座表统一 sys_ 前缀，唯独这张日志表叫 oper_log。
--       改名要两侧同步（实体 @TableName、查询接口、存量库 rename），且教培日志库已有生产数据，
--       所以本基线先按现状抄平，让两条路径长出同一个结构；改名另立议题。
--
-- 本表是追加型流水表：无软删列、无唯一键，因此不涉及"唯一键含 deleted"那条约定。
-- ============================================================================

-- 操作留痕日志表(独立于业务库,由 auditFlyway 单独迁移)
-- 记录范围:写操作(POST/PUT/DELETE/PATCH)+ 登录/登出;GET 查询不记录。
-- params 字段存请求参数,敏感字段(手机号/身份证/银行卡等)已按 DataMasking 打码,
-- 字段注释与代码侧 OperLogEntity 的 @Schema 保持一致。
CREATE TABLE oper_log (
    id BIGINT NOT NULL COMMENT '主键(雪花ID)',
    trace_id VARCHAR(64) NULL COMMENT '请求链路ID(对应 X-Trace-Id)',
    user_id BIGINT NULL COMMENT '操作人ID',
    username VARCHAR(64) NULL COMMENT '操作人账号(匿名操作为 system/anonymous)',
    org_id BIGINT NULL COMMENT '操作人所属机构ID',
    portal VARCHAR(16) NULL COMMENT '操作端口(admin=管理后台,其余取值由业务侧 PortalResolver 决定)',
    operation VARCHAR(128) NULL COMMENT '操作名称(来自Controller @Operation.summary)',
    action_type VARCHAR(16) NULL COMMENT '操作类型(CREATE=新增,UPDATE=修改,DELETE=删除,LOGIN=登录,LOGOUT=登出,OTHER=其他)',
    method VARCHAR(8) NULL COMMENT 'HTTP方法(GET/POST/PUT/DELETE/PATCH)',
    path VARCHAR(255) NULL COMMENT '请求路径',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP(取 X-Forwarded-For,否则 remoteAddr)',
    user_agent VARCHAR(512) NULL COMMENT '客户端User-Agent',
    params TEXT NULL COMMENT '请求参数JSON(敏感字段已脱敏打码)',
    error_msg VARCHAR(1024) NULL COMMENT '异常信息(失败时记录)',
    success TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功(1=成功,0=失败)',
    duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '接口耗时(毫秒)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_operlog_time (created_at),
    KEY idx_operlog_user (user_id),
    KEY idx_operlog_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作留痕日志(写操作与登录登出)';
