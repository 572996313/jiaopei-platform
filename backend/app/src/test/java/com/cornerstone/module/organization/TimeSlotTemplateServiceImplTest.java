package com.cornerstone.module.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotTemplateEntity;
import com.cornerstone.module.organization.dto.TimeSlotApplyRequest;
import com.cornerstone.module.organization.dto.TimeSlotApplyResponse;
import com.cornerstone.module.organization.dto.TimeSlotApplySlot;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.cornerstone.module.organization.mapper.TimeSlotTemplateMapper;
import com.cornerstone.module.organization.service.impl.TimeSlotTemplateServiceImpl;
import com.cornerstone.module.organization.service.support.DictValueValidator;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSlotTemplateServiceImplTest {

    @Mock
    private TimeSlotTemplateMapper templateMapper;

    @Mock
    private TimeSlotMapper timeSlotMapper;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private SystemDictService systemDictService;

    /** 时段类型走字典校验，apply 用例需要命中启用条目；不参与校验的用例不读它，故 lenient */
    @BeforeEach
    void stubSlotTypeDict() {
        lenient().when(systemDictService.listItems("slot_type")).thenReturn(List.of(
            new DictItemView(61L, 11L, "morning_study", "早自习", "morning_study", 10, "active", null),
            new DictItemView(62L, 11L, "teaching", "授课", "teaching", 20, "active", null),
            new DictItemView(63L, 11L, "break", "课间休息", "break", 30, "active", null),
            new DictItemView(64L, 11L, "self_study", "晚自习", "self_study", 40, "active", null)));
    }

    private TimeSlotTemplateServiceImpl service() {
        return new TimeSlotTemplateServiceImpl(templateMapper, timeSlotMapper, campusMapper,
            new ObjectMapper(), new DictValueValidator(systemDictService));
    }

    private TimeSlotTemplateEntity template(Long id, Long institutionId) {
        TimeSlotTemplateEntity entity = new TimeSlotTemplateEntity();
        entity.setId(id);
        entity.setInstitutionId(institutionId);
        entity.setTemplateName("标准工作日课表");
        entity.setConfigJson("{\"lessonDurationMinutes\":45}");
        entity.setStatus("active");
        entity.setDeleted(Boolean.FALSE);
        return entity;
    }

    private TimeSlotEntity slotRow(Long id, int weekday, String code, String name, LocalTime start, LocalTime end) {
        TimeSlotEntity entity = new TimeSlotEntity();
        entity.setId(id);
        entity.setInstitutionId(1L);
        entity.setTemplateId(10L);
        entity.setWeekday(weekday);
        entity.setSlotCode(code);
        entity.setSlotName(name);
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setDurationMinutes((int) java.time.temporal.ChronoUnit.MINUTES.between(start, end));
        entity.setSlotType("teaching");
        entity.setStatus("active");
        entity.setDeleted(Boolean.FALSE);
        return entity;
    }

    private TimeSlotApplySlot slot(int weekday, String code, String name, String start, String end) {
        return new TimeSlotApplySlot(weekday, code, name, LocalTime.parse(start), LocalTime.parse(end), "teaching", weekday * 100);
    }

    @Test
    void applyReusesExistingRowIdAndInsertsMissingSlots() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 1L));
        TimeSlotEntity existing = slotRow(100L, 1, "AM1", "上1", LocalTime.of(7, 0), LocalTime.of(7, 45));
        TimeSlotApplyRequest request = new TimeSlotApplyRequest(null, List.of(
            slot(1, "AM1", "上1", "07:55", "08:40"),
            slot(1, "AM2", "上2", "08:50", "09:35")));
        // 第一次 selectList 返回本模板存量行,第二次返回外部占用行,第三次返回结果集
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(existing), List.of(), List.of(existing));

        TimeSlotApplyResponse response = service().applyTemplate(1L, 10L, request);

        assertEquals(1, response.createdCount());
        assertEquals(1, response.updatedCount());
        assertEquals(0, response.removedCount());
        // 已有行必须复用主键更新(排课外键指向该行,禁止删旧插新)
        ArgumentCaptor<TimeSlotEntity> updated = ArgumentCaptor.forClass(TimeSlotEntity.class);
        verify(timeSlotMapper).updateById(updated.capture());
        assertEquals(100L, updated.getValue().getId());
        assertEquals(LocalTime.of(7, 55), updated.getValue().getStartTime());
        assertEquals(45, updated.getValue().getDurationMinutes());
        verify(timeSlotMapper).insert(any(TimeSlotEntity.class));
    }

    @Test
    void applyRemovesUnreferencedOrphanAndDeactivatesReferencedOrphan() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 1L));
        TimeSlotEntity kept = slotRow(100L, 1, "AM1", "上1", LocalTime.of(7, 55), LocalTime.of(8, 40));
        TimeSlotEntity orphanFree = slotRow(101L, 1, "AM9", "上9", LocalTime.of(15, 0), LocalTime.of(15, 45));
        TimeSlotEntity orphanUsed = slotRow(102L, 1, "AM8", "上8", LocalTime.of(16, 0), LocalTime.of(16, 45));
        TimeSlotApplyRequest request = new TimeSlotApplyRequest(null, List.of(
            slot(1, "AM1", "上1", "07:55", "08:40")));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(kept, orphanFree, orphanUsed), List.of(), List.of(kept));
        when(timeSlotMapper.countLessonRefs(101L)).thenReturn(0L);
        when(timeSlotMapper.countLessonRefs(102L)).thenReturn(2L);

        TimeSlotApplyResponse response = service().applyTemplate(1L, 10L, request);

        assertEquals(1, response.removedCount());
        assertEquals(1, response.deactivatedCount());
        verify(timeSlotMapper).deletePhysically(101L);
        // 保留行(匹配复用)与被停用行各更新一次
        ArgumentCaptor<TimeSlotEntity> updated = ArgumentCaptor.forClass(TimeSlotEntity.class);
        verify(timeSlotMapper, org.mockito.Mockito.times(2)).updateById(updated.capture());
        TimeSlotEntity deactivatedRow = updated.getAllValues().stream()
            .filter(value -> "inactive".equals(value.getStatus()))
            .findFirst()
            .orElseThrow();
        assertEquals(102L, deactivatedRow.getId());
    }

    @Test
    void applyRejectsOverlappingSlotsOnSameWeekday() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 1L));
        TimeSlotApplyRequest request = new TimeSlotApplyRequest(null, List.of(
            slot(1, "AM1", "上1", "07:55", "08:40"),
            slot(1, "AM2", "上2", "08:35", "09:20")));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().applyTemplate(1L, 10L, request));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(timeSlotMapper, never()).insert(any(TimeSlotEntity.class));
    }

    @Test
    void applyRejectsSlotConflictingWithManualRow() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 1L));
        TimeSlotEntity manual = slotRow(200L, 1, "AM1", "手工上1", LocalTime.of(7, 55), LocalTime.of(8, 40));
        manual.setTemplateId(null);
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(), List.of(manual));

        TimeSlotApplyRequest request = new TimeSlotApplyRequest(null, List.of(
            slot(1, "AM1", "上1", "07:55", "08:40")));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().applyTemplate(1L, 10L, request));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(timeSlotMapper, never()).insert(any(TimeSlotEntity.class));
    }

    @Test
    void applyRejectsOtherInstitutionTemplate() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 2L));
        TimeSlotApplyRequest request = new TimeSlotApplyRequest(null, List.of(
            slot(1, "AM1", "上1", "07:55", "08:40")));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().applyTemplate(1L, 10L, request));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
    }

    @Test
    void deleteTemplateRejectsWhenSlotsStillExist() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 1L));
        when(timeSlotMapper.selectCount(any())).thenReturn(5L);

        assertThrows(BusinessException.class, () -> service().deleteTemplate(1L, 10L));

        verify(templateMapper, never()).deletePhysically(10L);
    }
}
