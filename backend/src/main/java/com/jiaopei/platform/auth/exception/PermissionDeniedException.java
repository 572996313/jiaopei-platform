package com.jiaopei.platform.auth.exception;

import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;

public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException(String permission) {
        super(ResultCode.FORBIDDEN, "缺少所需权限：" + permission);
    }
}
