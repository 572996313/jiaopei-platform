package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.dto.TeacherUpdateRequest;
import com.jiaopei.platform.education.service.TeacherService;
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
@RequestMapping("/api/education/teachers")
@Tag(name = "教务管理")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<TeacherEntity>> list(@RequestParam(required = false) String institutionId,
                                                 @RequestParam(required = false) String campusId) {
        return ApiResponse.success("教师列表已加载", teacherService.listTeachers(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<TeacherEntity> create(@Valid @RequestBody TeacherCreateRequest request) {
        return ApiResponse.success("教师已创建", teacherService.createTeacher(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<TeacherEntity> update(@PathVariable Long id,
                                             @Valid @RequestBody TeacherUpdateRequest request) {
        return ApiResponse.success("教师已更新", teacherService.updateTeacher(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return ApiResponse.success("教师已删除", null);
    }
}
