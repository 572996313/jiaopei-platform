package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.dto.StudentCreateRequest;
import com.cornerstone.module.education.dto.StudentOptionView;
import com.cornerstone.module.education.dto.StudentPageQuery;
import com.cornerstone.module.education.dto.StudentUpdateRequest;
import com.cornerstone.module.education.service.StudentService;
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
@RequestMapping("/api/education/students")
@Tag(name = "教务管理")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @Operation(summary = "获取学员列表")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<StudentEntity>> list(@RequestParam(required = false) String institutionId,
                                                 @RequestParam(required = false) String keyword) {
        return ApiResponse.success("学员列表已加载", studentService.listStudents(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            keyword
        ));
    }

    @Operation(summary = "学员下拉选项", description = "按机构筛选,关键字模糊匹配姓名/学号,最多返回 30 条,供表单与筛选下拉按需搜索")
    @GetMapping("/options")
    @RequiresPermission("education:read")
    public ApiResponse<List<StudentOptionView>> options(@RequestParam(required = false) String institutionId,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.success("学员下拉选项已加载", studentService.listStudentOptions(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            keyword
        ));
    }

    @Operation(summary = "学员分页列表", description = "按机构筛选,关键字模糊匹配学号/姓名,分页返回")
    @GetMapping("/page")
    @RequiresPermission("education:read")
    public ApiResponse<PageResult<StudentEntity>> page(@Valid @ParameterObject StudentPageQuery query) {
        return ApiResponse.success("学员分页列表已加载", studentService.pageStudents(query));
    }

    @Operation(summary = "创建学员")
    @PostMapping
    @RequiresPermission("education:profile:write")
    public ApiResponse<StudentEntity> create(@Valid @RequestBody StudentCreateRequest request) {
        return ApiResponse.success("学员已创建", studentService.createStudent(request));
    }

    @Operation(summary = "更新学员")
    @PutMapping("/{id}")
    @RequiresPermission("education:profile:write")
    public ApiResponse<StudentEntity> update(@PathVariable Long id,
                                             @Valid @RequestBody StudentUpdateRequest request) {
        return ApiResponse.success("学员已更新", studentService.updateStudent(id, request));
    }

    @Operation(summary = "删除学员")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:profile:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ApiResponse.success("学员已删除", null);
    }
}
