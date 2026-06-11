package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.dto.ClassStudentCreateRequest;
import com.jiaopei.platform.education.service.ClassStudentService;
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
@RequestMapping({"/api/education/class-students", "/api/education/class_student"})
@Tag(name = "Education")
public class ClassStudentController {

    private final ClassStudentService classStudentService;

    public ClassStudentController(ClassStudentService classStudentService) {
        this.classStudentService = classStudentService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<ClassStudentEntity>> list(@RequestParam(required = false) String classId,
                                                      @RequestParam(required = false) String studentId) {
        return ApiResponse.success("Class student list loaded", classStudentService.listClassStudents(
            ExternalIdParser.parseOptional(classId, "classId"),
            ExternalIdParser.parseOptional(studentId, "studentId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<ClassStudentEntity> create(@Valid @RequestBody ClassStudentCreateRequest request) {
        return ApiResponse.success("Class student created", classStudentService.createClassStudent(request));
    }
}
