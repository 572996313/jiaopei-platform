package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.TeachingClassCreateRequest;
import com.cornerstone.module.education.service.TeachingClassService;
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
@RequestMapping("/api/education/classes")
@Tag(name = "教务管理")
public class TeachingClassController {

    private final TeachingClassService teachingClassService;

    public TeachingClassController(TeachingClassService teachingClassService) {
        this.teachingClassService = teachingClassService;
    }

    @Operation(summary = "获取班级列表")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<TeachingClassEntity>> list(@RequestParam(required = false) String institutionId,
                                                       @RequestParam(required = false) String campusId,
                                                       @RequestParam(required = false) String termId) {
        return ApiResponse.success("班级列表已加载", teachingClassService.listClasses(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId"),
            ExternalIdParser.parseOptional(termId, "termId")
        ));
    }

    @Operation(summary = "创建班级")
    @PostMapping
    @RequiresPermission("education:class:write")
    public ApiResponse<TeachingClassEntity> create(@Valid @RequestBody TeachingClassCreateRequest request) {
        return ApiResponse.success("班级已创建", teachingClassService.createClass(request));
    }

    @Operation(summary = "更新班级")
    @PutMapping("/{id}")
    @RequiresPermission("education:class:write")
    public ApiResponse<TeachingClassEntity> update(@PathVariable Long id,
                                                   @Valid @RequestBody TeachingClassCreateRequest request) {
        return ApiResponse.success("班级已更新", teachingClassService.updateClass(id, request));
    }

    @Operation(summary = "删除班级")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:class:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        teachingClassService.deleteClass(id);
        return ApiResponse.success("班级已删除", null);
    }
}
