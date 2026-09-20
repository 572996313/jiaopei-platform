package com.cornerstone.core.auth.exception;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;

public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException(String permission) {
        super(ResultCode.FORBIDDEN, "缺少所需权限：" + permission);
    }
}
