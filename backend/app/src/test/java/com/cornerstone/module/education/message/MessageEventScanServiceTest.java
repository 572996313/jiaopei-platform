package com.cornerstone.module.education.message;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.domain.entity.MsgSourceEntity;
import com.cornerstone.module.education.message.mapper.MsgEventMapper;
import com.cornerstone.module.education.message.mapper.MsgEventReadMapper;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.mapper.MsgSourceMapper;
import com.cornerstone.module.education.message.service.MessageEventScanService;
import com.cornerstone.module.education.message.support.MessageConditionEvaluator;
import com.cornerstone.module.education.message.support.MessageTemplateRenderer;
import com.cornerstone.module.education.message.support.MessageTypeRules;
import com.cornerstone.module.education.message.support.MessageViewQueryExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置化巡检的两条安全线：失败不消解、只在查询完整时才自动消解。
 *
 * <p>「截断/异常 → 跳过消解」是这套机制里最容易写错、后果最重的一条：
 * 一旦把没扫到的历史事件当成「条件已消失」，所有未结束事件都会被误标已解决，
 * 订阅者会看到提醒集体消失。这里用 Mockito 把这条线钉死。</p>
 */
class MessageEventScanServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0, 0);
    private static final long TYPE_ID = 9L;
    private static final String VIEW = "v_msg_demo";

    private final MsgSourceMapper sourceMapper = mock(MsgSourceMapper.class);
    private final MsgEventTypeMapper typeMapper = mock(MsgEventTypeMapper.class);
    private final MsgEventMapper eventMapper = mock(MsgEventMapper.class);
    private final MsgEventReadMapper readMapper = mock(MsgEventReadMapper.class);
    private final MessageViewQueryExecutor queryExecutor = mock(MessageViewQueryExecutor.class);
    private final MessageTemplateRenderer renderer = new MessageTemplateRenderer();

    private MessageEventScanService service;

    /** Lambda 列名解析依赖 MyBatis-Plus 的 TableInfo 缓存，纯单测没有容器启动，这里手动初始化一次 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), MsgEventEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new MessageEventScanService(sourceMapper, typeMapper, eventMapper, readMapper, queryExecutor,
            new MessageConditionEvaluator(new ObjectMapper()), renderer);
        when(sourceMapper.selectOne(any())).thenReturn(source());
    }

    @Test
    void truncationSkipsResolveAndReportsLastError() {
        when(queryExecutor.scan(VIEW)).thenReturn(new MessageViewQueryExecutor.ScanResult(List.of(row()), true));

        BusinessException error = assertThrows(BusinessException.class, () -> service.scanType(type(), NOW));
        assertTrue(error.getMessage().contains("跳过自动消解"));

        // 截断的那一轮：不写事件、不做消解，只把失败原因写回
        verify(eventMapper, never()).insert(any(MsgEventEntity.class));
        verify(eventMapper, never()).selectList(any());
        verify(queryExecutor, never()).columns(any());
        verify(typeMapper).updateRunResult(eq(TYPE_ID), eq(NOW), eq(0),
            argThat(message -> message != null && message.contains("超过")));
    }

    @Test
    void queryFailureSkipsResolveAndRecordsReason() {
        when(queryExecutor.scan(VIEW)).thenThrow(new IllegalStateException("视图查询超时"));

        assertThrows(IllegalStateException.class, () -> service.scanType(type(), NOW));

        verify(eventMapper, never()).insert(any(MsgEventEntity.class));
        verify(eventMapper, never()).selectList(any());
        verify(typeMapper).updateRunResult(eq(TYPE_ID), eq(NOW), eq(0), eq("视图查询超时"));
    }

    @Test
    void successfulScanInsertsMatchedRowAndResolvesMissingOne() {
        when(queryExecutor.scan(VIEW)).thenReturn(new MessageViewQueryExecutor.ScanResult(List.of(row(), notMatched()), false));
        when(queryExecutor.columns(VIEW)).thenReturn(columns());
        when(eventMapper.selectOne(any())).thenReturn(null);
        MsgEventEntity stale = new MsgEventEntity();
        stale.setId(55L);
        stale.setInstitutionId(1L);
        stale.setDedupeKey("attendance_missing:lesson:old");
        when(eventMapper.selectList(any())).thenReturn(List.of(stale));

        int matched = service.scanType(type(), NOW);

        assertEquals(1, matched);

        ArgumentCaptor<MsgEventEntity> inserted = ArgumentCaptor.forClass(MsgEventEntity.class);
        verify(eventMapper).insert(inserted.capture());
        MsgEventEntity event = inserted.getValue();
        assertEquals(1L, event.getInstitutionId().longValue());
        assertEquals("lesson_attendance_missing", event.getTypeCode());
        assertEquals("teaching", event.getCategory());
        assertEquals("warning", event.getLevel());
        assertEquals("三年二班 未完成点名", event.getTitle());
        assertEquals("应到 5 人，未点 2 人", event.getBody());
        assertEquals(MessageTypeRules.STATUS_ACTIVE, event.getStatus());
        assertEquals("attendance_missing:lesson:1001", event.getDedupeKey());
        assertEquals(NOW, event.getOccurredAt());
        assertEquals("/schedule?date=2026-11-26&focus=pending", event.getActionUrl());
        assertEquals(1001L, event.getAnchorLessonId().longValue());
        assertEquals(10L, event.getCampusId().longValue());

        // 只对没命中的那条 active 事件做自动消解
        verify(eventMapper).update(isNull(), any());
        verify(typeMapper).updateRunResult(eq(TYPE_ID), eq(NOW), eq(1), isNull());
    }

    @Test
    void reopenedEventClearsReadStateSoPeopleAreNotifiedAgain() {
        when(queryExecutor.scan(VIEW)).thenReturn(new MessageViewQueryExecutor.ScanResult(List.of(row()), false));
        when(queryExecutor.columns(VIEW)).thenReturn(columns());
        MsgEventEntity resolved = new MsgEventEntity();
        resolved.setId(77L);
        resolved.setStatus(MessageTypeRules.STATUS_RESOLVED);
        resolved.setOccurredAt(NOW.minusDays(2));
        when(eventMapper.selectOne(any())).thenReturn(resolved);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        int matched = service.scanType(type(), NOW);

        assertEquals(1, matched);
        // 已解决后条件再次成立：重开事件并清掉已读，否则原先读过的人永远看不到未读
        verify(eventMapper, never()).insert(any(MsgEventEntity.class));
        verify(readMapper).physicalDeleteByEventIds(List.of(77L));
        verify(typeMapper).updateRunResult(eq(TYPE_ID), eq(NOW), eq(1), isNull());
    }

    @Test
    void emptyDedupeKeyIsConfigErrorNotSilentDuplicate() {
        when(queryExecutor.scan(VIEW)).thenReturn(new MessageViewQueryExecutor.ScanResult(List.of(row()), false));
        when(queryExecutor.columns(VIEW)).thenReturn(columns());
        MsgEventTypeEntity type = type();
        type.setDedupeTemplate("${student_id}");

        assertThrows(BusinessException.class, () -> service.scanType(type, NOW));

        verify(eventMapper, never()).insert(any(MsgEventEntity.class));
        verify(typeMapper).updateRunResult(eq(TYPE_ID), eq(NOW), eq(0),
            argThat(message -> message != null && message.contains("去重键渲染为空")));
    }

    @Test
    void missingInstitutionIdIsRejected() {
        Map<String, Object> row = row();
        row.remove("institution_id");
        when(queryExecutor.scan(VIEW)).thenReturn(new MessageViewQueryExecutor.ScanResult(List.of(row), false));
        when(queryExecutor.columns(VIEW)).thenReturn(columns());

        assertThrows(BusinessException.class, () -> service.scanType(type(), NOW));

        verify(eventMapper, never()).insert(any(MsgEventEntity.class));
        verify(typeMapper).updateRunResult(eq(TYPE_ID), eq(NOW), eq(0),
            argThat(message -> message != null && message.contains("institution_id")));
    }

    @Test
    void templatePointingToUnknownColumnFailsBeforeWriting() {
        when(queryExecutor.scan(VIEW)).thenReturn(new MessageViewQueryExecutor.ScanResult(List.of(row()), false));
        when(queryExecutor.columns(VIEW)).thenReturn(columns());
        MsgEventTypeEntity type = type();
        type.setTitleTemplate("${class_name} ${no_such_column}");

        assertThrows(BusinessException.class, () -> service.scanType(type, NOW));

        verify(eventMapper, never()).insert(any(MsgEventEntity.class));
        verify(typeMapper).updateRunResult(eq(TYPE_ID), eq(NOW), eq(0),
            argThat(message -> message != null && message.contains("no_such_column")));
    }

    private static MsgEventTypeEntity type() {
        MsgEventTypeEntity type = new MsgEventTypeEntity();
        type.setId(TYPE_ID);
        type.setTypeCode("lesson_attendance_missing");
        type.setTypeName("未点名");
        type.setCategory("teaching");
        type.setSourceMode(MessageTypeRules.SOURCE_MODE_SCAN);
        type.setSourceCode("lesson_execution");
        type.setConditionJson("[{\"column\":\"missing_count\",\"op\":\"gt\",\"value\":0}]");
        type.setTitleTemplate("${class_name} 未完成点名");
        type.setBodyTemplate("应到 ${expected} 人，未点 ${missing_count} 人");
        type.setActionTemplate("/schedule?date=${lesson_date}&focus=pending");
        type.setDedupeTemplate("attendance_missing:lesson:${lesson_id}");
        type.setLevel("warning");
        type.setVisibility("internal");
        return type;
    }

    private static MsgSourceEntity source() {
        MsgSourceEntity source = new MsgSourceEntity();
        source.setSourceCode("lesson_execution");
        source.setViewName(VIEW);
        source.setEnabled(true);
        return source;
    }

    private static List<String> columns() {
        return List.of("institution_id", "campus_id", "class_name", "expected", "missing_count",
            "lesson_id", "lesson_date", "ref_type", "ref_id", "student_id");
    }

    private static Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("institution_id", 1L);
        row.put("campus_id", 10L);
        row.put("class_name", "三年二班");
        row.put("expected", 5);
        row.put("missing_count", 2);
        row.put("lesson_id", 1001L);
        row.put("lesson_date", LocalDate.of(2026, 11, 26));
        row.put("ref_type", "schedule_lesson");
        row.put("ref_id", 1001L);
        row.put("student_id", null);
        return row;
    }

    private static Map<String, Object> notMatched() {
        Map<String, Object> row = row();
        row.put("lesson_id", 1002L);
        row.put("missing_count", 0);
        return row;
    }
}
