package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherCreateRequest;
import com.cornerstone.module.education.dto.TeacherPageQuery;
import com.cornerstone.module.education.dto.TeacherUpdateRequest;
import com.cornerstone.module.education.service.TeacherService;
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
@RequestMapping("/api/education/teachers")
@Tag(name = "教务管理")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @Operation(summary = "获取教师列表")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<TeacherEntity>> list(@RequestParam(required = false) String institutionId,
                                                 @RequestParam(required = false) String campusId) {
        return ApiResponse.success("教师列表已加载", teacherService.listTeachers(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId")
        ));
    }

    @Operation(summary = "教师分页列表", description = "按机构/校区筛选,关键字模糊匹配姓名/工号/手机号,分页返回")
    @GetMapping("/page")
    @RequiresPermission("education:read")
    public ApiResponse<PageResult<TeacherEntity>> page(@Valid @ParameterObject TeacherPageQuery query) {
        return ApiResponse.success("教师分页列表已加载", teacherService.pageTeachers(query));
    }

    @Operation(summary = "创建教师")
    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<TeacherEntity> create(@Valid @RequestBody TeacherCreateRequest request) {
        return ApiResponse.success("教师已创建", teacherService.createTeacher(request));
    }

    @Operation(summary = "更新教师")
    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<TeacherEntity> update(@PathVariable Long id,
                                             @Valid @RequestBody TeacherUpdateRequest request) {
        return ApiResponse.success("教师已更新", teacherService.updateTeacher(id, request));
    }

    @Operation(summary = "删除教师")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return ApiResponse.success("教师已删除", null);
    }
}
