package com.jiaopei.platform.audit.dto;

import com.jiaopei.platform.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 操作日志分页查询参数。GET /api/system/operlogs 使用。
 */
@Schema(description = "操作日志分页查询参数")
public class OperLogPageQuery extends PageQuery {

    @Schema(description = "操作人(模糊匹配账号)")
    private String username;

    @Schema(description = "操作类型(CREATE=新增,UPDATE=修改,DELETE=删除,LOGIN=登录,LOGOUT=登出,OTHER=其他)", allowableValues = {"CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT", "OTHER"})
    private String actionType;

    @Schema(description = "操作端口(admin=管理后台,student=学生,parent=家长)", allowableValues = {"admin", "student", "parent"})
    private String portal;

    @Schema(description = "操作名称(模糊匹配,来自@Operation.summary)")
    private String operation;

    @Schema(description = "是否成功(1=成功,0=失败)", allowableValues = {"0", "1"})
    private Boolean success;

    @Schema(description = "操作时间起始(含),格式 yyyy-MM-dd HH:mm:ss")
    private String beginTime;

    @Schema(description = "操作时间结束(含),格式 yyyy-MM-dd HH:mm:ss")
    private String endTime;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getPortal() {
        return portal;
    }

    public void setPortal(String portal) {
        this.portal = portal;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(String beginTime) {
        this.beginTime = beginTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}