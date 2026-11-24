package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 一个后端接口的链路视图。
 *
 * <p>服务与数据表都是<b>类级</b>依赖：控制器注入了哪些服务、服务又注入了哪些 Mapper，
 * 因此同一服务上的多个接口会看到同一批表。这是有意的取舍——类级依赖靠反射就能拿到，
 * 方法级调用链需要读字节码，留到二期再评估。</p>
 */
@Schema(description = "一个后端接口的链路")
public record EndpointChainEndpointView(
    @Schema(description = "HTTP 方法，多个用逗号分隔，ANY 表示未限定") String method,
    @Schema(description = "请求路径") String path,
    @Schema(description = "接口说明（@Operation summary）") String summary,
    @Schema(description = "接口补充说明（@Operation description）") String description,
    @Schema(description = "权限码；为空表示该接口未声明权限校验") String permissionCode,
    @Schema(description = "所属模块：core / organization / education / other") String moduleKey,
    @Schema(description = "控制器类名") String controllerClass,
    @Schema(description = "控制器方法名") String controllerMethod,
    @Schema(description = "界面上会触发该接口的按钮") List<EndpointChainActionView> actions,
    @Schema(description = "接口所在控制器依赖的服务类") List<EndpointChainServiceView> services,
    @Schema(description = "服务声明依赖的 Mapper 对应的数据表") List<EndpointChainTableView> tables
) {
}
