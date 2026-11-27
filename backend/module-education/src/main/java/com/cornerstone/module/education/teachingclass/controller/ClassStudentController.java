package com.cornerstone.module.education.teachingclass.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentPageQuery;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentView;
import com.cornerstone.module.education.teachingclass.service.ClassStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/education/class-students", "/api/education/class_student"})
@Tag(name = "教务管理")
public class ClassStudentController {

    private final ClassStudentService classStudentService;

    public ClassStudentController(ClassStudentService classStudentService) {
        this.classStudentService = classStudentService;
    }

    @Operation(summary = "获取班级学员列表")
    @GetMapping
    public ApiResponse<List<ClassStudentEntity>> list(@RequestParam(required = false) String classId,
                                                      @RequestParam(required = false) String studentId) {
        return ApiResponse.success("班级学员列表已加载", classStudentService.listClassStudents(
            ExternalIdParser.parseOptional(classId, "classId"),
            ExternalIdParser.parseOptional(studentId, "studentId")
        ));
    }

    @Operation(summary = "分页获取班级学员关系", description = "按机构/校区/班级/学员/状态筛选,分页返回,已回带班级与学员名称")
    @GetMapping("/page")
    public ApiResponse<PageResult<ClassStudentView>> page(@Valid @ParameterObject ClassStudentPageQuery query) {
        return ApiResponse.success("班级学员分页列表已加载", classStudentService.pageClassStudents(query));
    }

    @Operation(summary = "统计班级在班人数", description = "按机构与班级统计 status=active 的学员数,与入班容量校验同一口径")
    @GetMapping("/active-count")
    public ApiResponse<Integer> activeCount(@RequestParam String institutionId,
                                            @RequestParam String classId) {
        return ApiResponse.success("班级在班人数已加载", classStudentService.countActiveStudents(
            ExternalIdParser.parseRequired(institutionId, "institutionId"),
            ExternalIdParser.parseRequired(classId, "classId")
        ));
    }

    @Operation(summary = "创建班级学员")
    @PostMapping
    @RequiresPermission("education:class:write")
    public ApiResponse<ClassStudentEntity> create(@Valid @RequestBody ClassStudentCreateRequest request) {
        return ApiResponse.success("班级学员已创建", classStudentService.createClassStudent(request));
    }

    @Operation(summary = "删除班级学员")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:class:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classStudentService.deleteClassStudent(id);
        return ApiResponse.success("班级学员已移除", null);
    }
}
