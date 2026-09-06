package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.dto.StudentCreateRequest;
import com.jiaopei.platform.education.dto.StudentUpdateRequest;
import com.jiaopei.platform.education.service.StudentService;
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
@RequestMapping("/api/education/students")
@Tag(name = "教务管理")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<StudentEntity>> list(@RequestParam(required = false) String institutionId,
                                                 @RequestParam(required = false) String campusId,
                                                 @RequestParam(required = false) String keyword) {
        return ApiResponse.success("学员列表已加载", studentService.listStudents(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId"),
            keyword
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<StudentEntity> create(@Valid @RequestBody StudentCreateRequest request) {
        return ApiResponse.success("学员已创建", studentService.createStudent(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<StudentEntity> update(@PathVariable Long id,
                                             @Valid @RequestBody StudentUpdateRequest request) {
        return ApiResponse.success("学员已更新", studentService.updateStudent(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ApiResponse.success("学员已删除", null);
    }
}
