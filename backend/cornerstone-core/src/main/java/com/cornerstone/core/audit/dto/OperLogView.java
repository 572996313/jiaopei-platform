package com.cornerstone.core.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 操作日志视图(查询返回)。
 */
@Schema(description = "操作日志视图")
public record OperLogView(
    @Schema(description = "主键(雪花ID)")
    Long id,

    @Schema(description = "请求链路ID")
    String traceId,

    @Schema(description = "操作人ID")
    Long userId,

    @Schema(description = "操作人账号")
    String username,

    @Schema(description = "操作人所属机构ID")
    Long orgId,

    @Schema(description = "操作端口(admin=管理后台;业务模块可自行扩展,本项目小程序端为 student/parent)")
    String portal,

    @Schema(description = "登录渠道(如 password/wechat_miniapp/linuxdo;非登录操作为空)")
    String loginProvider,

    @Schema(description = "操作名称")
    String operation,

    @Schema(description = "操作类型(CREATE=新增,UPDATE=修改,DELETE=删除,LOGIN=登录,LOGOUT=登出,OTHER=其他)", allowableValues = {"CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT", "OTHER"})
    String actionType,

    @Schema(description = "HTTP方法")
    String method,

    @Schema(description = "请求路径")
    String path,

    @Schema(description = "客户端IP")
    String clientIp,

    @Schema(description = "客户端User-Agent")
    String userAgent,

    @Schema(description = "请求参数(敏感字段已脱敏)")
    String params,

    @Schema(description = "异常信息(失败时记录)")
    String errorMsg,

    @Schema(description = "是否成功")
    Boolean success,

    @Schema(description = "接口耗时(毫秒)")
    Long durationMs,

    @Schema(description = "操作时间")
    LocalDateTime createdAt
) {
}