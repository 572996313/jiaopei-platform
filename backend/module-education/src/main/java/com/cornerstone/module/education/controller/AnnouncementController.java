package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.dto.AnnouncementPageQuery;
import com.cornerstone.module.education.dto.AnnouncementRecallRequest;
import com.cornerstone.module.education.dto.AnnouncementRecipientPreview;
import com.cornerstone.module.education.dto.AnnouncementUpsertRequest;
import com.cornerstone.module.education.dto.AnnouncementView;
import com.cornerstone.module.education.dto.MyAnnouncementView;
import com.cornerstone.module.education.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告中心。
 *
 * <p>管理端接口挂 announcement:read / announcement:write；/mine 三条是"我收到的公告"，
 * 任何登录账号（含小程序家长）都该看得到自己那一份，按 AGENTS 第 5 条**只认登录态、不挂权限码**，
 * 免得给接收端白送一个管理能力。</p>
 */
@Validated
@RestController
@RequestMapping("/api/education/announcements")
@Tag(name = "教务管理")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @Operation(summary = "分页查询公告", description = "按登录机构隔离，可按状态/类型/接收对象/标题关键字筛选")
    @GetMapping("/page")
    @RequiresPermission("announcement:read")
    public ApiResponse<PageResult<AnnouncementView>> page(@Valid AnnouncementPageQuery query) {
        return ApiResponse.success("公告列表已加载", announcementService.pageAnnouncements(query));
    }

    @Operation(summary = "查询公告详情", description = "含已读人数与发布/撤回留痕")
    @GetMapping("/{id}")
    @RequiresPermission("announcement:read")
    public ApiResponse<AnnouncementView> detail(@PathVariable Long id) {
        return ApiResponse.success("公告详情已加载", announcementService.getAnnouncement(id));
    }

    @Operation(summary = "试算公告触达人数", description = "按接收对象与校区/班级范围解析接收人，不落库；发布前给操作员确认")
    @PostMapping("/recipient-preview")
    @RequiresPermission("announcement:read")
    public ApiResponse<AnnouncementRecipientPreview> previewRecipients(
        @Valid @RequestBody AnnouncementUpsertRequest request) {
        return ApiResponse.success("触达人数已试算", announcementService.previewRecipients(request));
    }

    @Operation(summary = "新建公告草稿", description = "归属机构取发布人登录态；草稿阶段接收对象可以为空")
    @PostMapping
    @RequiresPermission("announcement:write")
    public ApiResponse<AnnouncementView> createDraft(@Valid @RequestBody AnnouncementUpsertRequest request) {
        return ApiResponse.success("草稿已保存", announcementService.createDraft(request));
    }

    @Operation(summary = "修改公告草稿", description = "只有草稿可改，已发布的公告正文冻结")
    @PutMapping("/{id}")
    @RequiresPermission("announcement:write")
    public ApiResponse<AnnouncementView> updateDraft(@PathVariable Long id,
                                                     @Valid @RequestBody AnnouncementUpsertRequest request) {
        return ApiResponse.success("草稿已更新", announcementService.updateDraft(id, request));
    }

    @Operation(summary = "发布公告", description = "解析接收人并展开成接收人行，同时冻结阅读进度的分母；解析为 0 人时拒绝发布")
    @PostMapping("/{id}/publish")
    @RequiresPermission("announcement:write")
    public ApiResponse<AnnouncementView> publish(@PathVariable Long id) {
        return ApiResponse.success("公告已发布", announcementService.publish(id));
    }

    @Operation(summary = "撤回公告", description = "接收端立即不可见，接收人行与分母保留作留痕")
    @PostMapping("/{id}/recall")
    @RequiresPermission("announcement:write")
    public ApiResponse<AnnouncementView> recall(@PathVariable Long id,
                                                @Valid @RequestBody AnnouncementRecallRequest request) {
        return ApiResponse.success("公告已撤回", announcementService.recall(id, request.reason()));
    }

    @Operation(summary = "删除公告草稿", description = "只删草稿；已发布的公告只能撤回")
    @DeleteMapping("/{id}")
    @RequiresPermission("announcement:write")
    public ApiResponse<Void> deleteDraft(@PathVariable Long id) {
        announcementService.deleteDraft(id);
        return ApiResponse.success("草稿已删除", null);
    }

    @Operation(summary = "我收到的公告", description = "登录账号自己的收件箱，按置顶、发布时间倒序；只认登录态")
    @GetMapping("/mine/page")
    public ApiResponse<PageResult<MyAnnouncementView>> myPage(
        @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") Integer page,
        @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
        @Max(value = 100, message = "每页条数不能超过 100") Integer pageSize,
        @RequestParam(defaultValue = "false") Boolean unreadOnly) {
        return ApiResponse.success("收件箱已加载",
            announcementService.pageMine(page, pageSize, Boolean.TRUE.equals(unreadOnly)));
    }

    @Operation(summary = "我的未读公告数", description = "小程序首页与未登录提示用；只认登录态")
    @GetMapping("/mine/unread-count")
    public ApiResponse<Long> myUnreadCount() {
        return ApiResponse.success("未读数已加载", announcementService.countMyUnread());
    }

    @Operation(summary = "打开我收到的公告", description = "非接收人、草稿、已撤回一律按不存在处理；首次打开记录已读时间")
    @GetMapping("/mine/{id}")
    public ApiResponse<MyAnnouncementView> openMine(@PathVariable Long id) {
        return ApiResponse.success("公告已加载", announcementService.openMine(id));
    }
}
