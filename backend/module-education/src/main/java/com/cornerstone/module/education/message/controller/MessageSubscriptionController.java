package com.cornerstone.module.education.message.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.message.dto.MessageRecommendationView;
import com.cornerstone.module.education.message.dto.MessageSubscriptionUpdateRequest;
import com.cornerstone.module.education.message.dto.MessageSubscriptionView;
import com.cornerstone.module.education.message.service.MessageSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的订阅。
 *
 * <p>自服务接口：读取只认登录态；保存挂 message:self:write（授给全部后台角色），
 * 避免教师订阅自己的班时被 403。订阅只收窄注意力，不是权限边界。</p>
 */
@Validated
@RestController
@RequestMapping("/api/education/message-subscriptions")
@Tag(name = "消息与事项")
public class MessageSubscriptionController {

    private final MessageSubscriptionService subscriptionService;

    public MessageSubscriptionController(MessageSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Operation(summary = "我的订阅规则", description = "空列表等于未收窄，看到全部可见事件")
    @GetMapping
    public ApiResponse<MessageSubscriptionView> getMine() {
        return ApiResponse.success("订阅规则已加载", subscriptionService.getMine());
    }

    @Operation(summary = "保存我的订阅规则", description = "整表替换；传空数组等于取消全部订阅，回到全部可见事件")
    @PutMapping
    @RequiresPermission("message:self:write")
    public ApiResponse<Void> saveMine(@Valid @RequestBody MessageSubscriptionUpdateRequest request) {
        subscriptionService.saveMine(request);
        return ApiResponse.success("订阅规则已保存", null);
    }

    @Operation(summary = "推荐订阅", description = "按任课班级、绑定学员、数据范围校区推荐；只推荐不自动生效")
    @GetMapping("/recommendations")
    public ApiResponse<List<MessageRecommendationView>> recommendations() {
        return ApiResponse.success("推荐订阅已加载", subscriptionService.recommendations());
    }
}
