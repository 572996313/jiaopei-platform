package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 一次"保存导航"提交的全部改动。
 *
 * 整批一次校验、一次落库：客户在这页是"先把结构摆好再点保存"，半批生效会让界面与库里的
 * 结构对不上，而这种对不上没有任何提示。
 */
@Schema(description = "导航编排保存请求")
public record NavigationUpdateRequest(
    @Schema(description = "本次要写的节点改动(只改这五个字段:名称、图标、顺序、所属分组、是否可见)")
    @NotNull(message = "节点改动列表不能为空")
    List<@Valid @NotNull NavigationNodeUpdateRequest> nodes
) {
}
