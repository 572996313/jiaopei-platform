package com.cornerstone.module.education.message;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.mapper.MsgEventLogMapper;
import com.cornerstone.module.education.message.mapper.MsgEventMapper;
import com.cornerstone.module.education.message.mapper.MsgEventReadMapper;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.service.MessageEventScanJob;
import com.cornerstone.module.education.message.service.MessageEventScanService;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 保留期清理必须物理删除。
 *
 * <p>事件唯一键含 {@code deleted}，逻辑删除每个去重键只容得下一条墓碑；同一课次的
 * 事件跨年再次过期清理时会撞唯一键。这里锁住「清理走物理删除」这条，防止回退。</p>
 */
class MessageEventScanJobTest {

    private final MsgEventTypeMapper typeMapper = mock(MsgEventTypeMapper.class);
    private final MsgEventMapper eventMapper = mock(MsgEventMapper.class);
    private final MsgEventReadMapper readMapper = mock(MsgEventReadMapper.class);
    private final MsgEventLogMapper logMapper = mock(MsgEventLogMapper.class);
    private final MessageEventScanService scanService = mock(MessageEventScanService.class);

    private final MessageEventScanJob job =
        new MessageEventScanJob(typeMapper, eventMapper, readMapper, logMapper, scanService);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), MsgEventEntity.class);
    }

    @Test
    void cleanupPhysicallyDeletesExpiredEventsAndChildren() {
        MsgEventTypeEntity type = new MsgEventTypeEntity();
        type.setTypeCode("fee_ledger_unpaid");
        type.setRetentionDays(30);
        when(typeMapper.selectList(any())).thenReturn(List.of(type));

        MsgEventEntity expired = new MsgEventEntity();
        expired.setId(5L);
        when(eventMapper.selectList(any())).thenReturn(List.of(expired));

        job.cleanup();

        // 子表先删，事件本体随后物理删除（不是逻辑删除）
        verify(readMapper).physicalDeleteByEventIds(List.of(5L));
        verify(logMapper).physicalDeleteByEventIds(List.of(5L));
        verify(eventMapper).physicalDeleteByIds(List.of(5L));
    }
}
