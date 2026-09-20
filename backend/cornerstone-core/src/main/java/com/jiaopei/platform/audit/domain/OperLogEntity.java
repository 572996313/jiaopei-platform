package com.jiaopei.platform.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 操作留痕日志实体。对应 jiaopei_log 库 oper_log 表。
 *
 * <p>不继承 BaseEntity:日志表在独立日志库,自含审计语义,
 * 不用业务库的 id/createdAt/createdBy/updatedAt/updatedBy/deleted 套路,
 * 且日志只增不改、无逻辑删除。</p>
 */
@Data
@TableName("oper_log")
@Schema(description = "操作留痕日志")
public class OperLogEntity {

    @Schema(description = "主键(雪花ID)")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "请求链路ID(对应 X-Trace-Id)")
    private String traceId;

    @Schema(description = "操作人ID")
    private Long userId;

    @Schema(description = "操作人账号(匿名操作为 system/anonymous)")
    private String username;

    @Schema(description = "操作人所属机构ID")
    private Long orgId;

    @Schema(description = "操作端口(admin=管理后台,student=学生,parent=家长)", allowableValues = {"admin", "student", "parent"})
    private String portal;

    @Schema(description = "操作名称(来自Controller @Operation.summary)")
    private String operation;

    @Schema(description = "操作类型(CREATE=新增,UPDATE=修改,DELETE=删除,LOGIN=登录,LOGOUT=登出,OTHER=其他)", allowableValues = {"CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT", "OTHER"})
    private String actionType;

    @Schema(description = "HTTP方法(GET/POST/PUT/DELETE/PATCH)", allowableValues = {"GET", "POST", "PUT", "DELETE", "PATCH"})
    private String method;

    @Schema(description = "请求路径")
    private String path;

    @Schema(description = "客户端IP(取 X-Forwarded-For,否则 remoteAddr)")
    private String clientIp;

    @Schema(description = "客户端User-Agent")
    private String userAgent;

    @Schema(description = "请求参数JSON(敏感字段已脱敏打码)")
    private String params;

    @Schema(description = "异常信息(失败时记录)")
    private String errorMsg;

    @Schema(description = "是否成功(1=成功,0=失败)", allowableValues = {"0", "1"})
    private Boolean success;

    @Schema(description = "接口耗时(毫秒)")
    private Long durationMs;

    @Schema(description = "操作时间")
    private LocalDateTime createdAt;
}