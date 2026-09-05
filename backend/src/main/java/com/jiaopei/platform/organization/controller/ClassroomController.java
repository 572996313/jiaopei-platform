package com.jiaopei.platform.organization.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.dto.ClassroomCreateRequest;
import com.jiaopei.platform.organization.dto.ClassroomUpdateRequest;
import com.jiaopei.platform.organization.service.ClassroomService;
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
@Tag(name = "Organization")
public class ClassroomController {

    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    @GetMapping
    @RequiresPermission("organization:read")
    public ApiResponse<List<ClassroomEntity>> list(@RequestParam(required = false) String campusId) {
        return ApiResponse.success("Classroom list loaded", classroomService.listClassrooms(ExternalIdParser.parseOptional(campusId, "campusId")));
    }

    @PostMapping
    @RequiresPermission("organization:write")
    public ApiResponse<ClassroomEntity> create(@Valid @RequestBody ClassroomCreateRequest request) {
        return ApiResponse.success("Classroom placeholder created", classroomService.createClassroom(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<ClassroomEntity> update(@PathVariable Long id,
                                               @Valid @RequestBody ClassroomUpdateRequest request) {
        return ApiResponse.success("Classroom updated", classroomService.updateClassroom(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("organization:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classroomService.deleteClassroom(id);
        return ApiResponse.success("Classroom deleted", null);
    }
}
