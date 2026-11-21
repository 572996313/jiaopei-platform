package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherAccountLinkCandidateResult;
import com.cornerstone.module.education.dto.TeacherAccountLinkRequest;
import com.cornerstone.module.education.dto.TeacherAccountLinkResult;
import com.cornerstone.module.education.dto.TeacherCreateRequest;
import com.cornerstone.module.education.dto.TeacherPageQuery;
import com.cornerstone.module.education.dto.TeacherUpdateRequest;
import com.cornerstone.module.education.service.TeacherAccountLinkService;
import com.cornerstone.module.education.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TeacherService teacherService;
    private final TeacherAccountLinkService teacherAccountLinkService;

    public TeacherController(TeacherService teacherService,
                             TeacherAccountLinkService teacherAccountLinkService) {
        this.teacherService = teacherService;
        this.teacherAccountLinkService = teacherAccountLinkService;
    }

    @Operation(summary = "获取教师列表")
    @GetMapping
    public ApiResponse<List<TeacherEntity>> list(@RequestParam(required = false) String institutionId,
                                                 @RequestParam(required = false) String campusId) {
        return ApiResponse.success("教师列表已加载", teacherService.listTeachers(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(campusId, "campusId")
        ));
    }

    @Operation(summary = "教师分页列表", description = "按机构/校区筛选,关键字模糊匹配姓名/工号/手机号,分页返回")
    @GetMapping("/page")
    public ApiResponse<PageResult<TeacherEntity>> page(@Valid @ParameterObject TeacherPageQuery query) {
        return ApiResponse.success("教师分页列表已加载", teacherService.pageTeachers(query));
    }

    @Operation(summary = "创建教师")
    @PostMapping
    @RequiresPermission("education:profile:write")
    public ApiResponse<TeacherEntity> create(@Valid @RequestBody TeacherCreateRequest request) {
        return ApiResponse.success("教师已创建", teacherService.createTeacher(request));
    }

    @Operation(summary = "更新教师")
    @PutMapping("/{id}")
    @RequiresPermission("education:profile:write")
    public ApiResponse<TeacherEntity> update(@PathVariable Long id,
                                             @Valid @RequestBody TeacherUpdateRequest request) {
        return ApiResponse.success("教师已更新", teacherService.updateTeacher(id, request));
    }

    @Operation(summary = "删除教师")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:profile:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return ApiResponse.success("教师已删除", null);
    }

    @Operation(summary = "导出教师档案",
        description = "按与分页列表相同的筛选条件导出全部教师档案（不分页，单次上限 5000 条）。"
            + "取值用字典 item_value 与校区编码，列顺序与导入模板一致，改完可导回")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @ParameterObject TeacherPageQuery query) {
        byte[] workbook = teacherService.exportTeachers(query);
        String filename = URLEncoder.encode("教师档案.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "预览教师账号匹配候选",
        description = "列出本机构尚未绑定登录账号的教师档案，按手机号等值命中本机构账号给出候选与姓名一致性标记。"
            + "只读不写库，机构取当前登录账号")
    @GetMapping("/link-candidates")
    @RequiresPermission("education:profile:write")
    public ApiResponse<TeacherAccountLinkCandidateResult> linkCandidates() {
        return ApiResponse.success("教师账号匹配候选已加载", teacherAccountLinkService.previewCandidates());
    }

    @Operation(summary = "确认绑定教师登录账号",
        description = "按前端确认的「档案-账号」配对写入 jp_teacher.user_id；任一无效配对（跨机构、档案已绑定、"
            + "账号已被他人占用）整批拒绝。账号本身仍由底座的批量导入创建")
    @PostMapping("/link-accounts")
    @RequiresPermission("education:profile:write")
    public ApiResponse<TeacherAccountLinkResult> linkAccounts(@Valid @RequestBody TeacherAccountLinkRequest request) {
        TeacherAccountLinkResult result = teacherAccountLinkService.link(request);
        return ApiResponse.success("已绑定 " + result.linkedCount() + " 位教师账号", result);
    }
}
