package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.EnrollmentEntity;
import com.jiaopei.platform.education.dto.EnrollmentCreateRequest;
import com.jiaopei.platform.education.service.EnrollmentService;
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
@RequestMapping("/api/education/enrollments")
@Tag(name = "Education")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<EnrollmentEntity>> list(@RequestParam(required = false) String institutionId,
                                                    @RequestParam(required = false) String classId,
                                                    @RequestParam(required = false) String studentId) {
        return ApiResponse.success("Enrollment list loaded", enrollmentService.listEnrollments(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(classId, "classId"),
            ExternalIdParser.parseOptional(studentId, "studentId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<EnrollmentEntity> create(@Valid @RequestBody EnrollmentCreateRequest request) {
        return ApiResponse.success("Enrollment created", enrollmentService.createEnrollment(request));
    }
}
