package com.jiaopei.platform.common.api;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS("SUCCESS", "Request completed successfully"),
    BAD_REQUEST("BAD_REQUEST", "Request payload is invalid"),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required"),
    FORBIDDEN("FORBIDDEN", "Current user has no permission"),
    NOT_FOUND("NOT_FOUND", "Requested resource does not exist"),
    VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Unexpected internal server error");

    private final String code;
    private final String defaultMessage;

    ResultCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
