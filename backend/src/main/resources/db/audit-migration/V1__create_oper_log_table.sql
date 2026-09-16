-- 操作留痕日志表(独立于业务库 jiaopei_log,由 auditFlyway 单独迁移)
-- 记录范围:写操作(POST/PUT/DELETE/PATCH)+ 登录/登出;GET 查询不记录。
-- params 字段存请求参数,敏感字段(手机号/身份证/银行卡等)已按 DataMasking 打码,
-- 字段注释与代码侧 OperLogEntity 的 @Schema 保持一致。

CREATE TABLE oper_log (
    id BIGINT NOT NULL COMMENT '主键(雪花ID)',
    trace_id VARCHAR(64) NULL COMMENT '请求链路ID(对应 X-Trace-Id)',
    user_id BIGINT NULL COMMENT '操作人ID',
    username VARCHAR(64) NULL COMMENT '操作人账号(匿名操作为 system/anonymous)',
    org_id BIGINT NULL COMMENT '操作人所属机构ID',
    portal VARCHAR(16) NULL COMMENT '操作端口(admin=管理后台,student=学生,parent=家长)',
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