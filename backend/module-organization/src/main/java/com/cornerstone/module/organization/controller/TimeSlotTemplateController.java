package com.cornerstone.module.organization.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.organization.dto.TimeSlotApplyRequest;
import com.cornerstone.module.organization.dto.TimeSlotApplyResponse;
import com.cornerstone.module.organization.dto.TimeSlotTemplateCreateRequest;
import com.cornerstone.module.organization.dto.TimeSlotTemplateUpdateRequest;
import com.cornerstone.module.organization.dto.TimeSlotTemplateView;
import com.cornerstone.module.organization.service.TimeSlotTemplateService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/time-slot-templates")
@Tag(name = "组织管理")
public class TimeSlotTemplateController {

    private final TimeSlotTemplateService timeSlotTemplateService;

    public TimeSlotTemplateController(TimeSlotTemplateService timeSlotTemplateService) {
        this.timeSlotTemplateService = timeSlotTemplateService;
    }

    @Operation(summary = "获取时段模板列表", description = "按机构(必填登录用户所属机构)与可选校区筛选")
    @GetMapping
    public ApiResponse<List<TimeSlotTemplateView>> list(@RequestParam(required = false) String institutionId,
                                                        @RequestParam(required = false) String campusId) {
        Long operatorInstitutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("时段模板列表已加载", timeSlotTemplateService.listTemplates(
            operatorInstitutionId,
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId")));
    }

    @Operation(summary = "创建时段模板")
    @PostMapping
    @RequiresPermission("organization:write")
    public ApiResponse<TimeSlotTemplateView> create(@Valid @RequestBody TimeSlotTemplateCreateRequest request) {
        return ApiResponse.success("时段模板已创建",
            timeSlotTemplateService.createTemplate(AuthContext.requireUser().orgId(), request));
    }

    @Operation(summary = "更新时段模板", description = "字段可选,仅更新传入项")
    @PutMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<TimeSlotTemplateView> update(@PathVariable Long id,
                                                    @Valid @RequestBody TimeSlotTemplateUpdateRequest request) {
        return ApiResponse.success("时段模板已更新",
            timeSlotTemplateService.updateTemplate(AuthContext.requireUser().orgId(), id, request));
    }

    @Operation(summary = "删除时段模板",
        description = "级联处理模板下的节次:未被排课引用的物理删除,被引用的改为停用并断开来源")
    @DeleteMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        timeSlotTemplateService.deleteTemplate(AuthContext.requireUser().orgId(), id);
        return ApiResponse.success("时段模板已删除", null);
    }

    @Operation(summary = "套用时段模板", description = "把网格编辑器确认的节次批量展开保存:按(星期,编码)匹配复用已有时间段,新增则插入,移除且未被排课引用则删除,被引用则停用;slots 传空数组即清空该模板下的全部节次")
    @PostMapping("/{id}/apply")
    @RequiresPermission("organization:write")
    public ApiResponse<TimeSlotApplyResponse> apply(@PathVariable Long id,
                                                    @Valid @RequestBody TimeSlotApplyRequest request) {
        return ApiResponse.success("时段模板已套用",
            timeSlotTemplateService.applyTemplate(AuthContext.requireUser().orgId(), id, request));
    }
}
