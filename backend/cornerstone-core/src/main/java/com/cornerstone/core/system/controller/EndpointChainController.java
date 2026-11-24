package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.EndpointChainView;
import com.cornerstone.core.system.service.EndpointChainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/endpoint-chains")
@Tag(name = "接口链路", description = "按接口查看权限码、界面按钮、控制器、服务与数据表；链路是声明依赖，不代表某次请求的实际调用")
public class EndpointChainController {

    private final EndpointChainService endpointChainService;

    public EndpointChainController(EndpointChainService endpointChainService) {
        this.endpointChainService = endpointChainService;
    }

    @GetMapping
    @RequiresPermission("system:endpoint:read")
    @Operation(summary = "接口链路", description = "一次返回全部接口的静态链路：HTTP 方法与路径、@Operation 说明、权限码、"
        + "触发它的界面按钮、控制器方法、依赖的服务类，以及服务声明依赖的 Mapper 对应的数据表。"
        + "结果为类级声明依赖，不区分某次请求实际读了还是写了；进程内缓存 60 秒")
    public ApiResponse<EndpointChainView> chains() {
        return ApiResponse.success("接口链路已加载", endpointChainService.chains());
    }
}
