package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.CourseCategoryEntity;
import com.jiaopei.platform.education.dto.CourseCategoryCreateRequest;
import com.jiaopei.platform.education.dto.CourseCategoryUpdateRequest;
import com.jiaopei.platform.education.service.CourseCategoryService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/education/course-categories")
@Tag(name = "教务管理")
public class CourseCategoryController {

    private final CourseCategoryService courseCategoryService;

    public CourseCategoryController(CourseCategoryService courseCategoryService) {
        this.courseCategoryService = courseCategoryService;
    }

    @Operation(summary = "获取课程分类列表")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<CourseCategoryEntity>> list(@RequestParam(required = false) String institutionId) {
        return ApiResponse.success("课程分类列表已加载", courseCategoryService.listCategories(
            ExternalIdParser.parseOptional(institutionId, "institutionId")
        ));
    }

    @Operation(summary = "创建课程分类")
    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<CourseCategoryEntity> create(@Valid @RequestBody CourseCategoryCreateRequest request) {
        return ApiResponse.success("课程分类已创建", courseCategoryService.createCategory(request));
    }

    @Operation(summary = "更新课程分类")
    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<CourseCategoryEntity> update(@PathVariable Long id,
                                                    @Valid @RequestBody CourseCategoryUpdateRequest request) {
        return ApiResponse.success("课程分类已更新", courseCategoryService.updateCategory(id, request));
    }

    @Operation(summary = "删除课程分类")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseCategoryService.deleteCategory(id);
        return ApiResponse.success("课程分类已删除", null);
    }
}
