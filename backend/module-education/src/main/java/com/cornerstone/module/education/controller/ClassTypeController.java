package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.dto.ClassTypeCreateRequest;
import com.cornerstone.module.education.dto.ClassTypeUpdateRequest;
import com.cornerstone.module.education.service.ClassTypeService;
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
@RequestMapping("/api/education/class-types")
@Tag(name = "教务管理")
public class ClassTypeController {

    private final ClassTypeService classTypeService;

    public ClassTypeController(ClassTypeService classTypeService) {
        this.classTypeService = classTypeService;
    }

    @Operation(summary = "获取班型列表")
    @GetMapping
    public ApiResponse<List<ClassTypeEntity>> list(@RequestParam(required = false) String institutionId) {
        return ApiResponse.success("班型列表已加载", classTypeService.listClassTypes(
            ExternalIdParser.parseOptional(institutionId, "institutionId")
        ));
    }

    @Operation(summary = "创建班型")
    @PostMapping
    @RequiresPermission("education:catalog:write")
    public ApiResponse<ClassTypeEntity> create(@Valid @RequestBody ClassTypeCreateRequest request) {
        return ApiResponse.success("班型已创建", classTypeService.createClassType(request));
    }

    @Operation(summary = "更新班型")
    @PutMapping("/{id}")
    @RequiresPermission("education:catalog:write")
    public ApiResponse<ClassTypeEntity> update(@PathVariable Long id,
                                               @Valid @RequestBody ClassTypeUpdateRequest request) {
        return ApiResponse.success("班型已更新", classTypeService.updateClassType(id, request));
    }

    @Operation(summary = "删除班型")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:catalog:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classTypeService.deleteClassType(id);
        return ApiResponse.success("班型已删除", null);
    }
}
