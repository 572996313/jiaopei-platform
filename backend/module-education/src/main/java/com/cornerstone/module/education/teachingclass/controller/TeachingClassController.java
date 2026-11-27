package com.cornerstone.module.education.teachingclass.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.teachingclass.dto.ClassOptionView;
import com.cornerstone.module.education.teachingclass.dto.TeachingClassCreateRequest;
import com.cornerstone.module.education.teachingclass.service.TeachingClassService;
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
    public ApiResponse<List<TeachingClassEntity>> list(@RequestParam(required = false) String institutionId,
                                                       @RequestParam(required = false) String campusId,
                                                       @RequestParam(required = false) String termId) {
        return ApiResponse.success("班级列表已加载", teachingClassService.listClasses(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId"),
            ExternalIdParser.parseOptional(termId, "termId")
        ));
    }

    @Operation(summary = "班级下拉选项",
            description = "按机构/校区/学期筛选,关键字模糊匹配班级名称与编码,最多返回 30 条,供表单与筛选下拉按需搜索;"
                    + "ids 传逗号分隔的班级ID时改为按ID批量回查(供表格行回显名称,上限 200 条),此时 keyword 不生效")
    @GetMapping("/options")
    public ApiResponse<List<ClassOptionView>> options(@RequestParam(required = false) String institutionId,
                                                      @RequestParam(required = false) String campusId,
                                                      @RequestParam(required = false) String termId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) List<Long> ids) {
        return ApiResponse.success("班级选项已加载", teachingClassService.listClassOptions(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId"),
            ExternalIdParser.parseOptional(termId, "termId"),
            keyword,
            ids
        ));
    }

    @Operation(summary = "统计班级数量",
            description = "按机构/校区/学期统计班级条数;下拉改成按需搜索后前端没有全量数组,统计卡的班级数由这里给")
    @GetMapping("/count")
    public ApiResponse<Long> count(@RequestParam(required = false) String institutionId,
                                   @RequestParam(required = false) String campusId,
                                   @RequestParam(required = false) String termId) {
        return ApiResponse.success("班级数量已加载", teachingClassService.countClasses(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId"),
            ExternalIdParser.parseOptional(termId, "termId")
        ));
    }

    @Operation(summary = "获取班级详情",
            description = "按班级ID取单个班级整条档案;institutionId 必传并与班级归属比对,避免按 id 直取漏掉机构范围")
    @GetMapping("/{id}")
    public ApiResponse<TeachingClassEntity> detail(@PathVariable Long id,
                                                   @RequestParam String institutionId) {
        return ApiResponse.success("班级详情已加载", teachingClassService.getClassDetail(
            id, ExternalIdParser.parseRequired(institutionId, "institutionId")));
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
