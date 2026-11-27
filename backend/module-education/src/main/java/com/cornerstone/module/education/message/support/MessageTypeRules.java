package com.cornerstone.module.education.message.support;

import java.util.List;
import java.util.Set;

/**
 * 事件流代码判定常量。
 *
 * <p>这些值都参与 if/switch 分支，按 AGENTS「枚举值/字典规范」不入字典；
 * 用户可选的「分类」才放 message_category 字典。</p>
 */
public final class MessageTypeRules {

    private MessageTypeRules() {
    }

    public static final String SOURCE_MODE_SCAN = "scan";
    public static final String SOURCE_MODE_MANUAL = "manual";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_CLOSED = "closed";

    public static final Set<String> LEVELS = Set.of("info", "warning", "critical");
    public static final Set<String> VISIBILITIES = Set.of("internal", "parent", "student");
    public static final Set<String> MANUAL_STATUSES = Set.of(
        STATUS_OPEN, STATUS_IN_PROGRESS, STATUS_DONE, STATUS_CLOSED);
    public static final Set<String> RULE_TYPES = Set.of(
        "type_code", "category", "campus", "class", "student", "teacher", "level", "keyword");

    /** 允许的深链前缀：只放本系统页面，拒绝 http(s)、协议相对地址与匿名跳转。 */
    public static final List<String> ACTION_PREFIXES = List.of(
        "/schedule", "/fee-ledger", "/classes", "/class-roster", "/students",
        "/attendance-overview", "/teachers", "/courses", "/messages");

    public static boolean isResolvedStatus(String status) {
        return STATUS_RESOLVED.equals(status)
            || STATUS_DONE.equals(status)
            || STATUS_CLOSED.equals(status)
            || "expired".equals(status);
    }

    public static boolean isManualStatus(String status) {
        return MANUAL_STATUSES.contains(status);
    }

    /**
     * 深链只放行本系统页面路径：必须单斜杠开头、不含反斜杠、命中 {@link #ACTION_PREFIXES}
     * 且不超过 500 字符。不合规返回 null（不跳转），由调用方决定是否记日志。
     */
    public static String sanitizeActionUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.length() > 500) {
            return null;
        }
        if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.contains("\\")) {
            return null;
        }
        for (String prefix : ACTION_PREFIXES) {
            if (trimmed.equals(prefix)
                || trimmed.startsWith(prefix + "?")
                || trimmed.startsWith(prefix + "/")) {
                return trimmed;
            }
        }
        return null;
    }
}
