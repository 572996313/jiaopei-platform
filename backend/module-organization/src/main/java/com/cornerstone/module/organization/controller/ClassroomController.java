package com.cornerstone.module.organization.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.dto.ClassroomCreateRequest;
import com.cornerstone.module.organization.dto.ClassroomUpdateRequest;
import com.cornerstone.module.organization.service.ClassroomService;
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
@RequestMapping("/api/organizations/classrooms")
@Tag(name = "组织管理")
public class ClassroomController {

    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    @Operation(summary = "获取教室列表")
    @GetMapping
    @RequiresPermission("organization:read")
    public ApiResponse<List<ClassroomEntity>> list(@RequestParam(required = false) String campusId) {
        return ApiResponse.success("教室列表已加载", classroomService.listClassrooms(ExternalIdParser.parseOptional(campusId, "campusId")));
    }

    @Operation(summary = "创建教室")
    @PostMapping
    @RequiresPermission("organization:write")
    public ApiResponse<ClassroomEntity> create(@Valid @RequestBody ClassroomCreateRequest request) {
        return ApiResponse.success("教室已创建", classroomService.createClassroom(request));
    }

    @Operation(summary = "更新教室")
    @PutMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<ClassroomEntity> update(@PathVariable Long id,
                                               @Valid @RequestBody ClassroomUpdateRequest request) {
        return ApiResponse.success("教室已更新", classroomService.updateClassroom(id, request));
    }

    @Operation(summary = "删除教室")
    @DeleteMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classroomService.deleteClassroom(id);
        return ApiResponse.success("教室已删除", null);
    }
}
