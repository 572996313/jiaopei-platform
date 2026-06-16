package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.CourseEntity;
import com.jiaopei.platform.education.dto.CourseCreateRequest;
import com.jiaopei.platform.education.service.CourseService;
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
@RequestMapping("/api/education/courses")
@Tag(name = "Education")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<CourseEntity>> list(@RequestParam(required = false) String institutionId,
                                                @RequestParam(required = false) String categoryId) {
        return ApiResponse.success("Course list loaded", courseService.listCourses(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(categoryId, "categoryId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<CourseEntity> create(@Valid @RequestBody CourseCreateRequest request) {
        return ApiResponse.success("Course created", courseService.createCourse(request));
    }
}
