package com.cornerstone.module.education.message.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.message.dto.MessageAssigneeView;
import com.cornerstone.module.education.message.dto.MessageEventDetailView;
import com.cornerstone.module.education.message.dto.MessageEventPageQuery;
import com.cornerstone.module.education.message.dto.MessageEventView;
import com.cornerstone.module.education.message.dto.MessageManualCreateRequest;
import com.cornerstone.module.education.message.dto.MessageManualUpdateRequest;
import com.cornerstone.module.education.message.dto.MessageNotifyRequest;
import com.cornerstone.module.education.message.dto.MessageTypeOptionView;
import java.util.List;

/**
 * 事件流读取与人工事项。
 *
 * <p>读取不按订阅做权限拦截：登录员工先看到本机构全部事件；订阅只影响「我的」视图。
 * 家长/学生端按可见端 + 绑定学员收窄。</p>
 */
public interface MessageEventService {

    PageResult<MessageEventView> pageStream(MessageEventPageQuery query);

    PageResult<MessageEventView> pageMine(MessageEventPageQuery query);

    long countUnread();

    MessageEventDetailView getDetail(Long id);

    MessageEventView createManual(MessageManualCreateRequest request);

    MessageEventView updateManual(Long id, MessageManualUpdateRequest request);

    int notifyUsers(Long id, MessageNotifyRequest request);

    List<MessageTypeOptionView> listTypeOptions();

    List<MessageAssigneeView> listAssignees(String keyword);
}
