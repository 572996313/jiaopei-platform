package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.dto.ScheduleRulePageQuery;
import com.cornerstone.module.education.dto.ScheduleRuleRequest;
import com.cornerstone.module.education.dto.ScheduleRuleView;
import com.cornerstone.module.education.service.ScheduleRuleService;
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

@RestController
@RequestMapping("/api/education/schedule-rules")
@Tag(name = "教务管理")
public class ScheduleRuleController {

    private final ScheduleRuleService scheduleRuleService;

    public ScheduleRuleController(ScheduleRuleService scheduleRuleService) {
        this.scheduleRuleService = scheduleRuleService;
    }

    @Operation(summary = "排课规则分页列表", description = "按机构/规则类型/规则对象/状态筛选,分页返回禁排必排规则")
    @GetMapping("/page")
    @RequiresPermission("education:read")
    public ApiResponse<PageResult<ScheduleRuleView>> page(@Valid @ParameterObject ScheduleRulePageQuery query) {
        return ApiResponse.success("排课规则分页列表已加载", scheduleRuleService.pageRules(query));
    }

    @Operation(summary = "获取排课规则详情")
    @GetMapping("/{id}")
    @RequiresPermission("education:read")
    public ApiResponse<ScheduleRuleView> get(@PathVariable Long id) {
        return ApiResponse.success("排课规则详情已加载", scheduleRuleService.getRule(id));
    }

    @Operation(summary = "新增排课规则", description = "为班级或教师登记星期×节次的禁排/必排约束")
    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleRuleView> create(@Valid @RequestBody ScheduleRuleRequest request) {
        return ApiResponse.success("排课规则已新增", scheduleRuleService.createRule(request));
    }

    @Operation(summary = "更新排课规则", description = "整体覆盖规则定义(含启停),不回改已生成课次")
    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleRuleView> update(@PathVariable Long id, @Valid @RequestBody ScheduleRuleRequest request) {
        return ApiResponse.success("排课规则已更新", scheduleRuleService.updateRule(id, request));
    }

    @Operation(summary = "删除排课规则")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleRuleService.deleteRule(id);
        return ApiResponse.success("排课规则已删除", null);
    }
}
