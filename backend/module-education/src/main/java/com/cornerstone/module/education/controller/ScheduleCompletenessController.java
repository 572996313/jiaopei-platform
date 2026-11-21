package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.dto.ScheduleCompletenessQuery;
import com.cornerstone.module.education.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.service.ScheduleCompletenessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/schedule-completeness")
@Tag(name = "教务管理")
public class ScheduleCompletenessController {

    private final ScheduleCompletenessService scheduleCompletenessService;

    public ScheduleCompletenessController(ScheduleCompletenessService scheduleCompletenessService) {
        this.scheduleCompletenessService = scheduleCompletenessService;
    }

    @Operation(summary = "排课完成度报表",
        description = "按班级×自然周汇总应排(启用中计划展开)/已生成/已完成/缺口/计划外,只统计登录用户所属机构")
    @GetMapping
    public ApiResponse<ScheduleCompletenessResponse> report(
        @Valid @ParameterObject ScheduleCompletenessQuery query) {
        return ApiResponse.success("排课完成度报表已加载",
            scheduleCompletenessService.report(AuthContext.requireUser().orgId(), query));
    }
}
