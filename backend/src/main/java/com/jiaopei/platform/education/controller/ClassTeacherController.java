package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.ClassTeacherEntity;
import com.jiaopei.platform.education.dto.ClassTeacherCreateRequest;
import com.jiaopei.platform.education.service.ClassTeacherService;
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
@RequestMapping({"/api/education/class-teachers", "/api/education/class_teacher"})
@Tag(name = "教务管理")
public class ClassTeacherController {

    private final ClassTeacherService classTeacherService;

    public ClassTeacherController(ClassTeacherService classTeacherService) {
        this.classTeacherService = classTeacherService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<ClassTeacherEntity>> list(@RequestParam(required = false) String classId,
                                                      @RequestParam(required = false) String teacherId) {
        return ApiResponse.success("班级教师列表已加载", classTeacherService.listClassTeachers(
            ExternalIdParser.parseOptional(classId, "classId"),
            ExternalIdParser.parseOptional(teacherId, "teacherId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<ClassTeacherEntity> create(@Valid @RequestBody ClassTeacherCreateRequest request) {
        return ApiResponse.success("班级教师已创建", classTeacherService.createClassTeacher(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<ClassTeacherEntity> update(@PathVariable Long id,
                                                  @Valid @RequestBody ClassTeacherCreateRequest request) {
        return ApiResponse.success("班级教师已更新", classTeacherService.updateClassTeacher(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classTeacherService.deleteClassTeacher(id);
        return ApiResponse.success("班级教师已删除", null);
    }
}
