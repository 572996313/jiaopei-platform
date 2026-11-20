package com.cornerstone.core.auth.scope;

import java.util.Collection;
import java.util.List;

/**
 * 数据范围维度（底座 SPI）：给角色授权界面提供选项、中文名与保存前校验。
 * 实现由业务模块提供，core 不 import 任何业务包。
 */
public interface RoleDataScopeDimension {

    /** 维度标识，与 {@link RoleDataScopeResolver#dimensionKey()} 一致。 */
    String dimensionKey();

    /** 维度中文名，如「可访问校区」。 */
    String dimensionLabel();

    /** 「全部」档文案，如「全部校区」。 */
    String allOptionLabel();

    /** 「指定」档文案，如「指定校区」。 */
    String partOptionLabel();

    /** 当前机构下可展示/可选的节点。 */
    List<RoleDataScopeOption> options(Long institutionId);

    /**
     * 校验保存值：必须属于该机构、未被删除，且业务上允许授权。
     * 不合法时直接抛 BusinessException（中文提示）。
     */
    void validate(Long institutionId, Collection<Long> values);
}
