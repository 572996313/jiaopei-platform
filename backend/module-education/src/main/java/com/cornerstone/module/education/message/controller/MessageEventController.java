package com.cornerstone.module.education.message.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.message.dto.MessageAssigneeView;
import com.cornerstone.module.education.message.dto.MessageEventDetailView;
import com.cornerstone.module.education.message.dto.MessageEventPageQuery;
import com.cornerstone.module.education.message.dto.MessageEventView;
import com.cornerstone.module.education.message.dto.MessageManualCreateRequest;
import com.cornerstone.module.education.message.dto.MessageManualUpdateRequest;
import com.cornerstone.module.education.message.dto.MessageNotifyRequest;
import com.cornerstone.module.education.message.dto.MessageTypeOptionView;
import com.cornerstone.module.education.message.service.MessageEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 事件中心（内部员工）。
 *
 * <p>读取接口登录即可：员工先看到本机构全部事件，「我的」只是按订阅收窄，不是权限边界。
 * 写接口挂 message:item:write，用于新建/编辑人工事项与提醒某人。</p>
 */
@Validated
@RestController
@RequestMapping("/api/education/messages")
@Tag(name = "消息与事项")
public class MessageEventController {

    private final MessageEventService eventService;

    public MessageEventController(MessageEventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "全量事件流", description = "按登录机构隔离；可按类型/分类/校区/班级/学员/教师/状态/级别/关键字/日期筛选")
    @GetMapping("/stream")
    public ApiResponse<PageResult<MessageEventView>> stream(@Valid MessageEventPageQuery query) {
        return ApiResponse.success("事件流已加载", eventService.pageStream(query));
    }

    @Operation(summary = "我关心的事件", description = "命中我的订阅规则或指派给我；没有订阅规则时等同于全部可见事件")
    @GetMapping("/mine")
    public ApiResponse<PageResult<MessageEventView>> mine(@Valid MessageEventPageQuery query) {
        return ApiResponse.success("我的事件已加载", eventService.pageMine(query));
    }

    @Operation(summary = "我的未读事件数", description = "只统计 active/open/in_progress，供铃铛角标轮询")
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.success("未读数已加载", eventService.countUnread());
    }

    @Operation(summary = "事件类型选项", description = "登录即可：筛选栏与新建事项的类型下拉")
    @GetMapping("/types")
    public ApiResponse<List<MessageTypeOptionView>> types() {
        return ApiResponse.success("事件类型已加载", eventService.listTypeOptions());
    }

    @Operation(summary = "可指派人", description = "本机构后台员工账号；家长/学生自助账号不参与指派")
    @GetMapping("/assignees")
    public ApiResponse<List<MessageAssigneeView>> assignees(
        @RequestParam(required = false) String keyword) {
        return ApiResponse.success("可指派人已加载", eventService.listAssignees(keyword));
    }

    @Operation(summary = "事件详情", description = "查看即记已读（GET 自服务，不挂写权限码）；含人工事项时间线")
    @GetMapping("/{id}")
    public ApiResponse<MessageEventDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success("事件详情已加载", eventService.getDetail(id));
    }

    @Operation(summary = "新建人工事项", description = "任务/反馈/问题；归属机构取登录态，可指派、设截止时间")
    @PostMapping
    @RequiresPermission("message:item:write")
    public ApiResponse<MessageEventView> createManual(@Valid @RequestBody MessageManualCreateRequest request) {
        return ApiResponse.success("事项已创建", eventService.createManual(request));
    }

    @Operation(summary = "编辑人工事项/流转状态", description = "系统巡检事件由条件自动消解，不允许手工改状态")
    @PutMapping("/{id}")
    @RequiresPermission("message:item:write")
    public ApiResponse<MessageEventView> updateManual(@PathVariable Long id,
                                                      @Valid @RequestBody MessageManualUpdateRequest request) {
        return ApiResponse.success("事项已更新", eventService.updateManual(id, request));
    }

    @Operation(summary = "提醒某人查看事件", description = "给每个被提醒人生成一条指派提醒事件；不改变原事件状态")
    @PostMapping("/{id}/notify")
    @RequiresPermission("message:item:write")
    public ApiResponse<Integer> notify(@PathVariable Long id,
                                       @Valid @RequestBody MessageNotifyRequest request) {
        return ApiResponse.success("提醒已发送", eventService.notifyUsers(id, request));
    }
}
