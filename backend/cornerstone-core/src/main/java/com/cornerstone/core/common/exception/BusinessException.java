package com.cornerstone.core.common.exception;

import com.cornerstone.core.common.api.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public BusinessException(String message) {
        this(ResultCode.BAD_REQUEST, message);
    }
}
