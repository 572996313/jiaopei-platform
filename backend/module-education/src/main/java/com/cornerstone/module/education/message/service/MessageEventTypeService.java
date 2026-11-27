package com.cornerstone.module.education.message.service;

import com.cornerstone.module.education.message.dto.MessageEventTypeUpsertRequest;
import com.cornerstone.module.education.message.dto.MessageEventTypeView;
import com.cornerstone.module.education.message.dto.MessageReadLedgerSummaryView;
import com.cornerstone.module.education.message.dto.MessageReaderView;
import com.cornerstone.module.education.message.dto.MessageSourceView;
import java.util.List;

/**
 * 事件目录运维：类型配置、数据源目录、阅读台账。
 *
 * <p>加事件类型/调阈值/停文案在这里完成，不走代码发布；只有出现新的数据形状才需要开发补视图。</p>
 */
public interface MessageEventTypeService {

    List<MessageEventTypeView> listTypes();

    MessageEventTypeView createType(MessageEventTypeUpsertRequest request);

    MessageEventTypeView updateType(String typeCode, MessageEventTypeUpsertRequest request);

    List<MessageSourceView> listSources();

    MessageSourceView validateSource(String sourceCode);

    List<MessageReadLedgerSummaryView> readLedger();

    List<MessageReaderView> readers(Long eventId);
}
