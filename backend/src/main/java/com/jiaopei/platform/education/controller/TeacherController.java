package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.service.TeacherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/teachers")
@Tag(name = "Education")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<TeacherEntity>> list(@RequestParam(required = false) String institutionId,
                                                 @RequestParam(required = false) String campusId) {
        return ApiResponse.success("Teacher list loaded", teacherService.listTeachers(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<TeacherEntity> create(@Valid @RequestBody TeacherCreateRequest request) {
        return ApiResponse.success("Teacher created", teacherService.createTeacher(request));
    }
}
