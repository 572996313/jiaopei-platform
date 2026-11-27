package com.cornerstone.module.education.student.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.StudentParentEntity;
import com.cornerstone.module.education.student.dto.StudentParentCreateRequest;
import com.cornerstone.module.education.student.dto.StudentParentPageQuery;
import com.cornerstone.module.education.student.dto.StudentParentView;
import com.cornerstone.module.education.student.service.StudentParentService;
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
@RequestMapping({"/api/education/student-parents", "/api/education/student_parent"})
@Tag(name = "教务管理")
public class StudentParentController {

    private final StudentParentService studentParentService;

    public StudentParentController(StudentParentService studentParentService) {
        this.studentParentService = studentParentService;
    }

    @Operation(summary = "获取家长联系人列表")
    @GetMapping
    public ApiResponse<List<StudentParentEntity>> list(@RequestParam(required = false) String institutionId,
                                                       @RequestParam(required = false) String studentId) {
        return ApiResponse.success("家长联系人列表已加载", studentParentService.listStudentParents(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(studentId, "studentId")
        ));
    }

    @Operation(summary = "家长联系人分页列表", description = "按机构/学员筛选,分页返回,已回带学员名称")
    @GetMapping("/page")
    public ApiResponse<PageResult<StudentParentView>> page(@Valid @ParameterObject StudentParentPageQuery query) {
        return ApiResponse.success("家长联系人分页列表已加载", studentParentService.pageStudentParents(query));
    }

    @Operation(summary = "创建家长联系人")
    @PostMapping
    @RequiresPermission("education:profile:write")
    public ApiResponse<StudentParentEntity> create(@Valid @RequestBody StudentParentCreateRequest request) {
        return ApiResponse.success("家长联系人已创建", studentParentService.createStudentParent(request));
    }

    @Operation(summary = "更新家长联系人")
    @PutMapping("/{id}")
    @RequiresPermission("education:profile:write")
    public ApiResponse<StudentParentEntity> update(@PathVariable Long id,
                                                   @Valid @RequestBody StudentParentCreateRequest request) {
        return ApiResponse.success("家长联系人已更新", studentParentService.updateStudentParent(id, request));
    }

    @Operation(summary = "删除家长联系人")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:profile:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        studentParentService.deleteStudentParent(id);
        return ApiResponse.success("家长联系人已删除", null);
    }
}
