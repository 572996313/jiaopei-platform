package com.cornerstone.core.auth.scope;

import java.util.Set;

/**
 * 登录快照里的一段数据范围。
 *
 * @param dimensionKey 维度标识，如 CAMPUS
 * @param restricted   false=不限制；true=按 values 白名单收窄
 * @param values       白名单；{@code restricted=true} 且为空集表示没有任何可见节点（拒绝而非放开）
 */
public record ResolvedDataScope(String dimensionKey, boolean restricted, Set<Long> values) {
}
