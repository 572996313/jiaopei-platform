package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.TeachingClassCreateRequest;
import com.jiaopei.platform.education.service.TeachingClassService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@Tag(name = "Education")
public class TeachingClassController {

    private final TeachingClassService teachingClassService;

    public TeachingClassController(TeachingClassService teachingClassService) {
        this.teachingClassService = teachingClassService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<TeachingClassEntity>> list(@RequestParam(required = false) String institutionId,
                                                       @RequestParam(required = false) String campusId,
                                                       @RequestParam(required = false) String termId) {
        return ApiResponse.success("Class list loaded", teachingClassService.listClasses(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId"),
            ExternalIdParser.parseOptional(termId, "termId")
        ));
    }

    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<TeachingClassEntity> create(@Valid @RequestBody TeachingClassCreateRequest request) {
        return ApiResponse.success("Class created", teachingClassService.createClass(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<TeachingClassEntity> update(@PathVariable Long id,
                                                   @Valid @RequestBody TeachingClassCreateRequest request) {
        return ApiResponse.success("Class updated", teachingClassService.updateClass(id, request));
    }
}
