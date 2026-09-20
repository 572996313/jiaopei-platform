package com.cornerstone.core.audit.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 操作类型分类。依据 HTTP 方法自动推断,登录/登出单独识别。
 */
@Schema(description = "操作类型")
public enum ActionType {

    /** 新增 */
    CREATE,

    /** 修改 */
    UPDATE,

    /** 删除 */
    DELETE,

    /** 登录 */
    LOGIN,

    /** 登出 */
    LOGOUT,

    /** 其他 */
    OTHER;

    /** 依据 HTTP 方法推断业务操作类型;非增删改方法归 OTHER。 */
    public static ActionType fromMethod(String method) {
        if (method == null) {
            return OTHER;
        }
        return switch (method.toUpperCase()) {
            case "POST" -> CREATE;
            case "PUT", "PATCH" -> UPDATE;
            case "DELETE" -> DELETE;
            default -> OTHER;
        };
    }
}