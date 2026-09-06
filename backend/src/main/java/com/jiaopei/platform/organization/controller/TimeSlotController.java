package com.jiaopei.platform.organization.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import com.jiaopei.platform.organization.dto.TimeSlotCreateRequest;
import com.jiaopei.platform.organization.dto.TimeSlotUpdateRequest;
import com.jiaopei.platform.organization.service.TimeSlotService;
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
@RequestMapping("/api/organizations/time-slots")
@Tag(name = "组织管理")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @GetMapping
    @RequiresPermission("organization:read")
    public ApiResponse<List<TimeSlotEntity>> list(@RequestParam(required = false) String institutionId) {
        return ApiResponse.success("时间段列表已加载", timeSlotService.listTimeSlots(ExternalIdParser.parseOptional(institutionId, "institutionId")));
    }

    @PostMapping
    @RequiresPermission("organization:write")
    public ApiResponse<TimeSlotEntity> create(@Valid @RequestBody TimeSlotCreateRequest request) {
        return ApiResponse.success("时间段已创建", timeSlotService.createTimeSlot(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<TimeSlotEntity> update(@PathVariable Long id,
                                              @Valid @RequestBody TimeSlotUpdateRequest request) {
        return ApiResponse.success("时间段已更新", timeSlotService.updateTimeSlot(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        timeSlotService.deleteTimeSlot(id);
        return ApiResponse.success("时间段已删除", null);
    }
}
