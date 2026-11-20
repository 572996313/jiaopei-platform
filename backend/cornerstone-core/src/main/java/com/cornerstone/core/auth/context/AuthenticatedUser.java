package com.cornerstone.core.auth.context;

import java.util.Set;

/**
 * 登录会话里的身份与权限快照（Redis 为准，JWT 只做签名与过期校验）。
 *
 * @param dataScopeKey 数据范围维度，如 CAMPUS；null=不限制
 * @param dataScopeIds 数据范围白名单；null=不限制，空集=没有任何可见节点（拒绝而非放开）
 */
public record AuthenticatedUser(
    Long userId,
    Long orgId,
    String username,
    Set<String> roles,
    Set<String> permissions,
    Boolean forceChangePassword,
    String portal,
    String dataScopeKey,
    Set<Long> dataScopeIds
) {

    /**
     * 兼容存量调用点：新增字段追加在末尾，旧构造默认不限制。
     * 新字段必须是引用类型，旧 Redis JSON 缺字段才能反序列化为 null（不能写成 boolean）。
     */
    public AuthenticatedUser(Long userId,
                             Long orgId,
                             String username,
                             Set<String> roles,
                             Set<String> permissions,
                             Boolean forceChangePassword,
                             String portal) {
        this(userId, orgId, username, roles, permissions, forceChangePassword, portal, null, null);
    }
}
