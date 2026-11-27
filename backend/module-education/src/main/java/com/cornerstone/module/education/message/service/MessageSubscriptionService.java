package com.cornerstone.module.education.message.service;

import com.cornerstone.module.education.message.dto.MessageRecommendationView;
import com.cornerstone.module.education.message.dto.MessageSubscriptionUpdateRequest;
import com.cornerstone.module.education.message.dto.MessageSubscriptionView;
import java.util.List;

/**
 * 个人订阅：从全量事件流里取自己关心的部分。
 *
 * <p>订阅不是权限边界，保存失败/漏勾选不会让人看不到事件；空规则等于全部可见事件。</p>
 */
public interface MessageSubscriptionService {

    MessageSubscriptionView getMine();

    void saveMine(MessageSubscriptionUpdateRequest request);

    List<MessageRecommendationView> recommendations();
}
