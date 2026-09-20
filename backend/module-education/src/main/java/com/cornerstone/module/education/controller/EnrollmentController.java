package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.EnrollmentEntity;
import com.cornerstone.module.education.dto.EnrollmentCreateRequest;
import com.cornerstone.module.education.dto.EnrollmentPageQuery;
import com.cornerstone.module.education.dto.EnrollmentView;
import com.cornerstone.module.education.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/enrollments")
@Tag(name = "教务管理")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @Operation(summary = "获取报名列表")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<EnrollmentEntity>> list(@RequestParam(required = false) String institutionId,
                                                    @RequestParam(required = false) String classId,
                                                    @RequestParam(required = false) String studentId) {
        return ApiResponse.success("报名列表已加载", enrollmentService.listEnrollments(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(classId, "classId"),
            ExternalIdParser.parseOptional(studentId, "studentId")
        ));
    }

    @Operation(summary = "报名记录分页列表", description = "按机构/班级/学员筛选,分页返回,已回带学员与班级名称")
    @GetMapping("/page")
    @RequiresPermission("education:read")
    public ApiResponse<PageResult<EnrollmentView>> page(@Valid @ParameterObject EnrollmentPageQuery query) {
        return ApiResponse.success("报名分页列表已加载", enrollmentService.pageEnrollments(query));
    }

    @Operation(summary = "创建报名")
    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<EnrollmentEntity> create(@Valid @RequestBody EnrollmentCreateRequest request) {
        return ApiResponse.success("报名已创建", enrollmentService.createEnrollment(request));
    }
}
