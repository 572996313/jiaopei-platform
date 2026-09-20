package com.cornerstone.core.audit.controller;

import com.cornerstone.core.audit.dto.OperLogPageQuery;
import com.cornerstone.core.audit.dto.OperLogView;
import com.cornerstone.core.audit.service.OperLogQueryService;
import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作留痕日志查询接口。查询本身不记录操作日志(读操作不入库,
 * 避免日志查询造成日志刷屏)。
 */
@RestController
@RequestMapping("/api/system/operlogs")
@Tag(name = "操作日志", description = "操作留痕日志查询")
public class OperLogController {

    private final OperLogQueryService operLogQueryService;

    public OperLogController(OperLogQueryService operLogQueryService) {
        this.operLogQueryService = operLogQueryService;
    }

    @Operation(summary = "操作日志分页查询", description = "按操作人/操作类型/端口/操作名/时间范围/成败筛选,按操作时间倒序")
    @GetMapping
    @RequiresPermission("system:operlog:read")
    public ApiResponse<PageResult<OperLogView>> page(@Validated @ParameterObject OperLogPageQuery query) {
        return ApiResponse.success("操作日志已加载", operLogQueryService.page(query));
    }
}