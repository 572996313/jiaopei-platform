package com.cornerstone.core.common.api;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS("SUCCESS", "请求处理成功"),
    BAD_REQUEST("BAD_REQUEST", "请求参数无效"),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或登录已失效"),
    FORBIDDEN("FORBIDDEN", "当前用户没有操作权限"),
    NOT_FOUND("NOT_FOUND", "请求的资源不存在"),
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数校验失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误，请稍后重试");

    private final String code;
    private final String defaultMessage;

    ResultCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
