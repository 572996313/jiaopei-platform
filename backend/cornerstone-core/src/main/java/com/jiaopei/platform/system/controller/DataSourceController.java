package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.system.dto.DataSourceCreateRequest;
import com.jiaopei.platform.system.dto.DataSourceDefinitionView;
import com.jiaopei.platform.system.dto.DataSourceUpdateRequest;
import com.jiaopei.platform.system.dto.DataSourceView;
import com.jiaopei.platform.system.service.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/data-sources")
@Tag(name = "数据源配置", description = "页面下拉选项的可配置数据源")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @Operation(summary = "按编码批量取数据源定义", description = "供通用组件消费,仅需登录,返回启用的数据源定义")
    @GetMapping("/definitions")
    public ApiResponse<Map<String, DataSourceDefinitionView>> definitions(
        @RequestParam(name = "codes") String codes) {
        List<String> list = Arrays.stream(codes.split(","))
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .toList();
        return ApiResponse.success("数据源定义已加载", dataSourceService.getDefinitions(list));
    }

    @GetMapping
    @RequiresPermission("system:data-source:manage")
    @Operation(summary = "获取全部数据源配置")
    public ApiResponse<List<DataSourceView>> list() {
        return ApiResponse.success("数据源配置已加载", dataSourceService.listDataSources());
    }

    @PostMapping
    @RequiresPermission("system:data-source:manage")
    @Operation(summary = "创建数据源")
    public ApiResponse<DataSourceView> create(@Valid @RequestBody DataSourceCreateRequest request) {
        return ApiResponse.success("数据源已创建", dataSourceService.createDataSource(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:data-source:manage")
    @Operation(summary = "更新数据源")
    public ApiResponse<DataSourceView> update(@PathVariable Long id,
                                              @Valid @RequestBody DataSourceUpdateRequest request) {
        return ApiResponse.success("数据源已更新", dataSourceService.updateDataSource(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:data-source:manage")
    @Operation(summary = "删除数据源")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataSourceService.deleteDataSource(id);
        return ApiResponse.success("数据源已删除", null);
    }
}