package com.jiaopei.platform.common.web;

import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;

public final class ExternalIdParser {

    private ExternalIdParser() {
    }

    public static Long parseOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequired(value, fieldName);
    }

    public static Long parseRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + " 不能为空");
        }

        String numeric = normalized.matches("\\d+")
            ? normalized
            : normalized.replaceFirst("^.*?(\\d+)$", "$1");
        if (!numeric.matches("\\d+")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + " 必须是数字 ID 或以数字结尾的业务编码");
        }

        try {
            return Long.valueOf(numeric);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + " 超出有效范围");
        }
    }
}
