package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.system.dto.ListConfigCreateRequest;
import com.jiaopei.platform.system.dto.ListConfigUpdateRequest;
import com.jiaopei.platform.system.dto.ListConfigView;
import com.jiaopei.platform.system.service.ListConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/list-configs")
@Tag(name = "列表页配置", description = "可配置列表页的展示列与查询条件配置")
public class ListConfigController {

    private final ListConfigService listConfigService;

    public ListConfigController(ListConfigService listConfigService) {
        this.listConfigService = listConfigService;
    }

    @Operation(summary = "按资源编码取启用的列表配置", description = "供通用列表组件消费,仅需登录,不要求配置管理权限")
    @GetMapping("/code/{resourceCode}")
    public ApiResponse<ListConfigView> getByResourceCode(@PathVariable String resourceCode) {
        ListConfigView view = listConfigService.getByResourceCode(resourceCode);
        if (view == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "列表配置不存在或未启用：" + resourceCode);
        }
        return ApiResponse.success("列表配置已加载", view);
    }

    @GetMapping
    @RequiresPermission("system:list-config:manage")
    @Operation(summary = "获取全部列表配置")
    public ApiResponse<List<ListConfigView>> list() {
        return ApiResponse.success("列表配置已加载", listConfigService.listConfigs());
    }

    @PostMapping
    @RequiresPermission("system:list-config:manage")
    @Operation(summary = "创建列表配置")
    public ApiResponse<ListConfigView> create(@Valid @RequestBody ListConfigCreateRequest request) {
        return ApiResponse.success("列表配置已创建", listConfigService.createConfig(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:list-config:manage")
    @Operation(summary = "更新列表配置")
    public ApiResponse<ListConfigView> update(@PathVariable Long id, @Valid @RequestBody ListConfigUpdateRequest request) {
        return ApiResponse.success("列表配置已更新", listConfigService.updateConfig(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:list-config:manage")
    @Operation(summary = "删除列表配置")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        listConfigService.deleteConfig(id);
        return ApiResponse.success("列表配置已删除", null);
    }
}
