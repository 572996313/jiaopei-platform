package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.CourseCategoryEntity;
import com.jiaopei.platform.education.dto.CourseCategoryCreateRequest;
import com.jiaopei.platform.education.service.CourseCategoryService;
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
@RequestMapping("/api/education/course-categories")
@Tag(name = "Education")
public class CourseCategoryController {

    private final CourseCategoryService courseCategoryService;

    public CourseCategoryController(CourseCategoryService courseCategoryService) {
        this.courseCategoryService = courseCategoryService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<CourseCategoryEntity>> list(@RequestParam(required = false) String institutionId) {
        return ApiResponse.success("Course category list loaded", courseCategoryService.listCategories(
            ExternalIdParser.parseOptional(institutionId, "institutionId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<CourseCategoryEntity> create(@Valid @RequestBody CourseCategoryCreateRequest request) {
        return ApiResponse.success("Course category created", courseCategoryService.createCategory(request));
    }
}
