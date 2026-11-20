package com.cornerstone.core.auth.scope;

import java.util.Collection;

/**
 * 角色数据范围解析器（底座 SPI）。实现由业务模块提供，底座不感知具体维度含义。
 *
 * <p>登录签发时，底座遍历所有实现；没有实现 = 不限制（模块未装配时行为不变）。</p>
 */
public interface RoleDataScopeResolver {

    /** 维度标识，由实现方定义，如 CAMPUS。 */
    String dimensionKey();

    /**
     * 解析这批角色在该机构下的范围并集。
     *
     * @return {@code null} 表示未配置/不限制；{@link ResolvedDataScope#restricted()} 为 true 时按白名单收窄
     */
    ResolvedDataScope resolve(Long institutionId, Collection<Long> roleIds);
}
