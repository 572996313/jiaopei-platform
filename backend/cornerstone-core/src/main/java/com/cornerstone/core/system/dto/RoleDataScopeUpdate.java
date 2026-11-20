package com.cornerstone.core.system.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 角色授权中某个数据范围维度的保存值。
 *
 * @param dimensionKey 维度标识，由 GET 授权详情原样带回，底座不硬编码具体维度
 * @param scopeMode    ALL=该维度全部；PART=仅 values 白名单
 * @param values       PART 时的节点ID；ALL 时应为空
 */
@Schema(description = "角色数据范围保存值")
public record RoleDataScopeUpdate(
    @Schema(description = "数据范围维度标识")
    @NotBlank(message = "数据范围维度不能为空")
    String dimensionKey,
    @Schema(description = "ALL=全部；PART=指定")
    @NotBlank(message = "数据范围模式不能为空")
    @Pattern(regexp = "ALL|PART", message = "数据范围模式只能是 ALL 或 PART")
    String scopeMode,
    @Schema(description = "指定范围时的节点ID列表")
    @JsonDeserialize(contentUsing = FlexibleLongIdDeserializer.class)
    List<Long> values
) {
}
