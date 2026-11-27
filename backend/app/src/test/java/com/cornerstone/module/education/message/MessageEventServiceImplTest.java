package com.cornerstone.module.education.message;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventReadEntity;
import com.cornerstone.module.education.message.domain.entity.MsgSubscriptionEntity;
import com.cornerstone.module.education.message.dto.MessageEventPageQuery;
import com.cornerstone.module.education.message.dto.MessageEventView;
import com.cornerstone.module.education.message.mapper.MsgEventLogMapper;
import com.cornerstone.module.education.message.mapper.MsgEventMapper;
import com.cornerstone.module.education.message.mapper.MsgEventReadMapper;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.mapper.MsgSubscriptionMapper;
import com.cornerstone.module.education.message.service.impl.MessageEventServiceImpl;
import com.cornerstone.module.education.message.support.MessageVisibilitySupport;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 事件列表映射：巡检事件没有指派/创建/消解人时，三个账号列全空。
 *
 * <p>回归点：{@code userNames} 在无账号可查时返回 {@code Map.of()}，
 * 直接 {@code get(null)} 会抛 NPE（不可变 Map 拒绝 null 键），表现为打开事件中心 500。
 * 这里用一条三列全空的巡检事件走完整分页映射链，钉住空账号列不炸。</p>
 */
class MessageEventServiceImplTest {

    private static final long USER_ID = 9L;

    private final MsgEventMapper eventMapper = mock(MsgEventMapper.class);
    private final MsgEventReadMapper readMapper = mock(MsgEventReadMapper.class);
    private final MsgEventTypeMapper typeMapper = mock(MsgEventTypeMapper.class);
    private final MsgEventLogMapper logMapper = mock(MsgEventLogMapper.class);
    private final MsgSubscriptionMapper subscriptionMapper = mock(MsgSubscriptionMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final MessageVisibilitySupport visibilitySupport = mock(MessageVisibilitySupport.class);

    private final MessageEventServiceImpl service = new MessageEventServiceImpl(
        eventMapper, readMapper, typeMapper, logMapper, subscriptionMapper, userAccountMapper,
        classMapper, studentMapper, teacherMapper, campusMapper, visibilitySupport);

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MsgEventEntity.class);
        TableInfoHelper.initTableInfo(assistant, MsgEventReadEntity.class);
        TableInfoHelper.initTableInfo(assistant, MsgSubscriptionEntity.class);
    }

    @BeforeEach
    void setUp() {
        AuthenticatedUser user = new AuthenticatedUser(
            USER_ID, 1L, "tester", Set.of("TEACHER"), Set.of(), false, "PC");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of()));

        when(subscriptionMapper.selectList(any())).thenReturn(List.of());
        when(typeMapper.selectList(any())).thenReturn(List.of());
        when(readMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scannedEventWithoutAnyUserColumnsMapsWithoutNpe() {
        MsgEventEntity event = new MsgEventEntity();
        event.setId(5L);
        event.setInstitutionId(1L);
        event.setTypeCode("fee_ledger_unpaid");
        event.setCategory("fee");
        event.setLevel("warning");
        event.setTitle("学员费用未收齐");
        event.setBody("应收 100 元，实收 0 元");
        event.setStatus("active");
        event.setDedupeKey("fee_unpaid:ledger:1");
        event.setOccurredAt(LocalDateTime.now());

        Page<MsgEventEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(event));
        page.setTotal(1);
        when(eventMapper.selectPage(any(), any())).thenReturn(page);

        MessageEventPageQuery query = new MessageEventPageQuery();
        query.setOpenOnly(true);
        query.setUnreadOnly(true);

        List<MessageEventView> records = service.pageMine(query).records();

        assertEquals(1, records.size());
        MessageEventView view = records.get(0);
        assertEquals("fee_ledger_unpaid", view.typeCode());
        // 类型目录为空时回退显示类型码，不能是空白
        assertEquals("fee_ledger_unpaid", view.typeName());
        assertNull(view.assigneeUserId());
        assertNull(view.assigneeName());
        assertNull(view.createdByName());
        assertNull(view.resolvedByName());
        assertFalse(view.read());
        assertNull(view.readAt());
    }
}
