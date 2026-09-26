package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.system.dto.SystemConfigCreateRequest;
import com.cornerstone.core.system.dto.SystemConfigPageQuery;
import com.cornerstone.core.system.dto.SystemConfigUpdateRequest;
import com.cornerstone.core.system.dto.SystemConfigView;
import com.cornerstone.core.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/config")
@Tag(name = "系统参数配置", description = "系统参数配置管理")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    @RequiresPermission("system:config:read")
    @Operation(summary = "获取系统参数列表")
    public List<SystemConfigView> listConfigs() {
        return systemConfigService.listConfigs();
    }

    @GetMapping("/page")
    @RequiresPermission("system:config:read")
    @Operation(summary = "系统参数分页列表", description = "支持关键字(配置键/显示名称/配置说明)、分组、状态筛选")
    public PageResult<SystemConfigView> pageConfigs(@Valid @ParameterObject SystemConfigPageQuery query) {
        return systemConfigService.pageConfigs(query);
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:config:read")
    @Operation(summary = "获取系统参数详情")
    public SystemConfigView getConfig(@PathVariable Long id) {
        return systemConfigService.getConfig(id);
    }

    @PostMapping
    @RequiresPermission("system:config:write")
    @Operation(summary = "创建系统参数")
    public SystemConfigView createConfig(@Valid @RequestBody SystemConfigCreateRequest request) {
        return systemConfigService.createConfig(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:config:write")
    @Operation(summary = "更新系统参数")
    public SystemConfigView updateConfig(@PathVariable Long id, @Valid @RequestBody SystemConfigUpdateRequest request) {
        return systemConfigService.updateConfig(id, request);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:config:write")
    @Operation(summary = "删除系统参数")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        // 必须返回标准信封:void 会产生 200 空 body,前端 unwrap 校验 success 字段时
        // 会把这次成功的删除误判为失败(弹"请求失败"且不刷新列表)
        systemConfigService.deleteConfig(id);
        return ApiResponse.success("系统参数已删除", null);
    }
}
