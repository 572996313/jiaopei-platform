package com.cornerstone.module.education.schedule.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.schedule.dto.SchedulePlanCommitRequest;
import com.cornerstone.module.education.schedule.dto.SchedulePlanCommitResponse;
import com.cornerstone.module.education.schedule.dto.SchedulePlanPageQuery;
import com.cornerstone.module.education.schedule.dto.SchedulePlanPreviewResponse;
import com.cornerstone.module.education.schedule.dto.SchedulePlanRequest;
import com.cornerstone.module.education.schedule.dto.SchedulePlanView;
import com.cornerstone.module.education.schedule.service.SchedulePlanService;
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
@RequestMapping("/api/education/schedule-plans")
@Tag(name = "教务管理")
public class SchedulePlanController {

    private final SchedulePlanService schedulePlanService;

    public SchedulePlanController(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }

    @Operation(summary = "排课计划分页列表", description = "按机构/校区/班级/状态/生效区间筛选,分页返回排课计划")
    @GetMapping("/page")
    public ApiResponse<PageResult<SchedulePlanView>> page(@Valid @ParameterObject SchedulePlanPageQuery query) {
        return ApiResponse.success("排课计划分页列表已加载", schedulePlanService.pagePlans(query));
    }

    @Operation(summary = "获取排课计划详情")
    @GetMapping("/{id}")
    public ApiResponse<SchedulePlanView> get(@PathVariable Long id) {
        return ApiResponse.success("排课计划详情已加载", schedulePlanService.getPlan(id));
    }

    @Operation(summary = "排课计划展开预览", description = "按每周节次模式与起止日期展开课次草案,逐条标记冲突与跳过日期,不落库")
    @PostMapping("/preview")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<SchedulePlanPreviewResponse> preview(@Valid @RequestBody SchedulePlanRequest request) {
        return ApiResponse.success("排课计划预览已生成", schedulePlanService.preview(request));
    }

    @Operation(summary = "提交排课计划生成课次", description = "保存计划并按勾选课次生成;计划ID为空则新建,已生成的键自动跳过(幂等补排)")
    @PostMapping("/commit")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<SchedulePlanCommitResponse> commit(@Valid @RequestBody SchedulePlanCommitRequest request) {
        SchedulePlanCommitResponse response = schedulePlanService.commit(request);
        return ApiResponse.success("排课计划已提交,生成课次 " + response.createdCount() + " 条", response);
    }

    @Operation(summary = "更新排课计划", description = "仅更新计划定义(含启停),不回改已生成课次")
    @PutMapping("/{id}")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<SchedulePlanView> update(@PathVariable Long id, @Valid @RequestBody SchedulePlanRequest request) {
        return ApiResponse.success("排课计划已更新", schedulePlanService.updatePlan(id, request));
    }

    @Operation(summary = "删除排课计划", description = "仅删除计划本身,已生成课次不受影响")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        schedulePlanService.deletePlan(id);
        return ApiResponse.success("排课计划已删除", null);
    }

    @Operation(summary = "撤销计划未上课次", description = "软删该计划生成且日期不早于今天的已排课/已调课课次,返回撤销条数")
    @DeleteMapping("/{id}/lessons")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<Integer> cancelLessons(@PathVariable Long id) {
        int cancelled = schedulePlanService.cancelPlanLessons(id);
        return ApiResponse.success("已撤销 " + cancelled + " 条未上课次", cancelled);
    }
}
