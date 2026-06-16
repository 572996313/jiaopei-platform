package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.StudentParentEntity;
import com.jiaopei.platform.education.dto.StudentParentCreateRequest;
import com.jiaopei.platform.education.service.StudentParentService;
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
@RequestMapping({"/api/education/student-parents", "/api/education/student_parent"})
@Tag(name = "Education")
public class StudentParentController {

    private final StudentParentService studentParentService;

    public StudentParentController(StudentParentService studentParentService) {
        this.studentParentService = studentParentService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<StudentParentEntity>> list(@RequestParam(required = false) String institutionId,
                                                       @RequestParam(required = false) String studentId) {
        return ApiResponse.success("Student parent list loaded", studentParentService.listStudentParents(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(studentId, "studentId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<StudentParentEntity> create(@Valid @RequestBody StudentParentCreateRequest request) {
        return ApiResponse.success("Student parent created", studentParentService.createStudentParent(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<StudentParentEntity> update(@PathVariable Long id,
                                                   @Valid @RequestBody StudentParentCreateRequest request) {
        return ApiResponse.success("Student parent updated", studentParentService.updateStudentParent(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        studentParentService.deleteStudentParent(id);
        return ApiResponse.success("Student parent deleted", null);
    }
}
