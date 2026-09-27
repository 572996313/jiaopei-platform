package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.dto.ScheduleAutoBatchRequest;
import com.cornerstone.module.education.dto.ScheduleAutoBatchView;
import com.cornerstone.module.education.dto.ScheduleAutoPlanResponse;
import com.cornerstone.module.education.dto.ScheduleAutoRollbackResponse;
import com.cornerstone.module.education.service.ScheduleAutoBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/schedule-auto-batches")
@Tag(name = "教务管理")
public class ScheduleAutoBatchController {

    private final ScheduleAutoBatchService autoBatchService;

    public ScheduleAutoBatchController(ScheduleAutoBatchService autoBatchService) {
        this.autoBatchService = autoBatchService;
    }

    @Operation(summary = "自动补排预览", description = "按完成度报表的缺口排出每一格与未排上的原因,不落库;教师沿用班级既有计划,窗口按自然周对齐")
    @PostMapping("/preview")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<ScheduleAutoPlanResponse> preview(@Valid @RequestBody ScheduleAutoBatchRequest request) {
        return ApiResponse.success("补排草案已生成", autoBatchService.preview(request));
    }

    @Operation(summary = "执行自动补排", description = "按与预览同一份决策生成课次并记为一个批次;一格都没排上不建批次")
    @PostMapping("/run")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<ScheduleAutoPlanResponse> run(@Valid @RequestBody ScheduleAutoBatchRequest request) {
        return ApiResponse.success("自动补排已执行", autoBatchService.run(request));
    }

    @Operation(summary = "自动补排批次列表", description = "按机构/校区取最近 20 个补排批次,按执行时间倒序全量返回(数据量小,不分页)")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<ScheduleAutoBatchView>> list(@RequestParam(required = false) Long institutionId,
                                                        @RequestParam(required = false) Long campusId) {
        return ApiResponse.success("批次清单已加载", autoBatchService.listBatches(institutionId, campusId));
    }

    @Operation(summary = "撤销自动补排批次", description = "软删该批次生成且今天之后的已排课/已调课/补课课次并连带删除授课关系;已点名或已过期的课次保留并在结果里说明")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<ScheduleAutoRollbackResponse> rollback(@PathVariable Long id) {
        return ApiResponse.success("批次已撤销", autoBatchService.rollback(id));
    }
}
