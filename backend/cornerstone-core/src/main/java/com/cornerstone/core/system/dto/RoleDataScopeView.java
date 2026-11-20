package com.cornerstone.core.system.dto;

import com.cornerstone.core.auth.scope.RoleDataScopeOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 角色授权弹窗里的一个数据范围维度。
 *
 * @param configured false=没有任何授权行，等同「全部」，用于界面提示存量角色
 * @param values     PART 时的白名单；ALL 时为空
 * @param options    当前机构下可选的节点（含停用项，便于移除）
 */
@Schema(description = "角色数据范围")
public record RoleDataScopeView(
    @Schema(description = "数据范围维度标识") String dimensionKey,
    @Schema(description = "维度中文名") String dimensionLabel,
    @Schema(description = "全部档文案") String allOptionLabel,
    @Schema(description = "指定档文案") String partOptionLabel,
    @Schema(description = "ALL=全部；PART=指定") String scopeMode,
    @Schema(description = "是否已经显式配置过") Boolean configured,
    @Schema(description = "指定范围时的节点ID列表") List<Long> values,
    @Schema(description = "可选节点") List<RoleDataScopeOption> options
) {
}
