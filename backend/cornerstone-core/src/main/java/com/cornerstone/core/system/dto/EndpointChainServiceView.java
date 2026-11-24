package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 接口依赖的一个服务类。接口名取控制器字段的声明类型，类名取实际实现，
 * 两者相同时（控制器直接注入实现类）只显示一次。
 */
@Schema(description = "接口依赖的服务类")
public record EndpointChainServiceView(
    @Schema(description = "控制器字段的声明类型（通常是接口）") String interfaceName,
    @Schema(description = "实际实现类") String className,
    @Schema(description = "所属模块") String moduleKey
) {
}
