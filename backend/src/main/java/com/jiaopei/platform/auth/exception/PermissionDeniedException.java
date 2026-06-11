package com.jiaopei.platform.auth.exception;

import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;

public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException(String permission) {
        super(ResultCode.FORBIDDEN, "Missing required permission: " + permission);
    }
}
