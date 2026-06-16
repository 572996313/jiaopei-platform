package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.ClassTypeEntity;
import com.jiaopei.platform.education.dto.ClassTypeCreateRequest;
import com.jiaopei.platform.education.service.ClassTypeService;
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
@RequestMapping("/api/education/class-types")
@Tag(name = "Education")
public class ClassTypeController {

    private final ClassTypeService classTypeService;

    public ClassTypeController(ClassTypeService classTypeService) {
        this.classTypeService = classTypeService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<ClassTypeEntity>> list(@RequestParam(required = false) String institutionId) {
        return ApiResponse.success("Class type list loaded", classTypeService.listClassTypes(
            ExternalIdParser.parseOptional(institutionId, "institutionId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<ClassTypeEntity> create(@Valid @RequestBody ClassTypeCreateRequest request) {
        return ApiResponse.success("Class type created", classTypeService.createClassType(request));
    }
}
