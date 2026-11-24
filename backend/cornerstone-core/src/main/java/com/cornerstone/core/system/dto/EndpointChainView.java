package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 接口链路总览：把"按钮 → 权限码 → 接口 → 控制器 → 服务 → 数据表"这条静态依赖链摊开。
 *
 * <p>口径必须说死：链路来自代码里的<b>声明依赖</b>（控制器/服务持有哪些字段），
 * 不是某一次请求的实际调用记录。页面只能写"可能涉及"，不能写"本次修改了"。</p>
 */
@Schema(description = "接口链路总览")
public record EndpointChainView(
    @Schema(description = "生成时间") String generatedAt,
    @Schema(description = "口径说明：声明依赖，不代表某次请求的实际调用") String note,
    @Schema(description = "接口数量") int endpointCount,
    @Schema(description = "带权限码的接口数量") int permissionCount,
    @Schema(description = "接口链路列表") List<EndpointChainEndpointView> endpoints
) {
}
