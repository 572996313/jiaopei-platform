package com.cornerstone.module.education.announcement.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.announcement.dto.AnnouncementTemplateUpsertRequest;
import com.cornerstone.module.education.announcement.dto.AnnouncementTemplateView;
import com.cornerstone.module.education.announcement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告模板。
 *
 * <p>路径没放在 {@code /announcements/templates}：{@code AnnouncementController} 有
 * {@code GET /announcements/{id}}，{@code templates} 会被当成公告 ID 走类型转换。单独一条路径最省事。</p>
 */
@Validated
@RestController
@RequestMapping("/api/education/announcement-templates")
@Tag(name = "教务管理")
public class AnnouncementTemplateController {

    private final AnnouncementService announcementService;

    public AnnouncementTemplateController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @Operation(summary = "查询公告模板", description = "当前机构的全部模板，按创建时间倒序；新建公告时用于预填")
    @GetMapping
    public ApiResponse<List<AnnouncementTemplateView>> list() {
        return ApiResponse.success("模板列表已加载", announcementService.listTemplates());
    }

    @Operation(summary = "新建公告模板", description = "归属机构取建模板的人的登录态；范围字段按公告同一套规则校验")
    @PostMapping
    @RequiresPermission("announcement:write")
    public ApiResponse<AnnouncementTemplateView> create(
        @Valid @RequestBody AnnouncementTemplateUpsertRequest request) {
        return ApiResponse.success("模板已保存", announcementService.createTemplate(request));
    }

    @Operation(summary = "修改公告模板", description = "只能改本机构模板；模板内容可直接改，不影响已发布的公告")
    @PutMapping("/{id}")
    @RequiresPermission("announcement:write")
    public ApiResponse<AnnouncementTemplateView> update(@PathVariable Long id,
                                                       @Valid @RequestBody AnnouncementTemplateUpsertRequest request) {
        return ApiResponse.success("模板已更新", announcementService.updateTemplate(id, request));
    }

    @Operation(summary = "删除公告模板", description = "模板与公告没有外键关系，删除模板不会动已发出去的公告")
    @DeleteMapping("/{id}")
    @RequiresPermission("announcement:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        announcementService.deleteTemplate(id);
        return ApiResponse.success("模板已删除", null);
    }
}
