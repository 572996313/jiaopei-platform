package com.cornerstone.core.audit.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 操作端口(来源域)。admin=管理后台(平台用户),student=学生端,parent=家长端。
 */
@Schema(description = "操作端口")
public enum Portal {

    /** 管理后台(平台用户) */
    admin,

    /** 学生端 */
    student,

    /** 家长端 */
    parent;

    /**
     * 兼容字符串解析:空或未知返回 null(记录为 null),避免历史脏数据导致 NPE。
     */
    public static Portal parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Portal portal : values()) {
            if (portal.name().equalsIgnoreCase(value.trim())) {
                return portal;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name();
    }
}