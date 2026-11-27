package com.cornerstone.module.education.course.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.course.dto.CourseCreateRequest;
import com.cornerstone.module.education.course.dto.CoursePageQuery;
import com.cornerstone.module.education.course.dto.CourseUpdateRequest;
import com.cornerstone.module.education.course.service.CourseService;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
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
@RequestMapping("/api/education/courses")
@Tag(name = "教务管理")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "获取课程列表")
    @GetMapping
    public ApiResponse<List<CourseEntity>> list(@RequestParam(required = false) String institutionId,
                                                @RequestParam(required = false) String categoryId) {
        return ApiResponse.success("课程列表已加载", courseService.listCourses(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(categoryId, "categoryId")
        ));
    }

    @Operation(summary = "课程分页列表", description = "按机构/分类筛选,关键字模糊匹配课程名称/编号/学科,分页返回")
    @GetMapping("/page")
    public ApiResponse<PageResult<CourseEntity>> page(@Valid @ParameterObject CoursePageQuery query) {
        return ApiResponse.success("课程分页列表已加载", courseService.pageCourses(query));
    }

    @Operation(summary = "创建课程")
    @PostMapping
    @RequiresPermission("education:catalog:write")
    public ApiResponse<CourseEntity> create(@Valid @RequestBody CourseCreateRequest request) {
        return ApiResponse.success("课程已创建", courseService.createCourse(request));
    }

    @Operation(summary = "更新课程")
    @PutMapping("/{id}")
    @RequiresPermission("education:catalog:write")
    public ApiResponse<CourseEntity> update(@PathVariable Long id,
                                            @Valid @RequestBody CourseUpdateRequest request) {
        return ApiResponse.success("课程已更新", courseService.updateCourse(id, request));
    }

    @Operation(summary = "删除课程")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:catalog:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ApiResponse.success("课程已删除", null);
    }
}
