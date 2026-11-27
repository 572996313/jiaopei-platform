package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.schedule.dto.TimeSlotLessonCleanupResponse;
import com.cornerstone.module.education.schedule.dto.TimeSlotLessonRefView;
import com.cornerstone.module.education.schedule.service.impl.TimeSlotLessonRefServiceImpl;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeSlotLessonRefServiceImplTest {

    @Mock
    private TimeSlotMapper timeSlotMapper;

    @Mock
    private ScheduleLessonMapper lessonMapper;

    @Mock
    private ScheduleTeacherRelMapper teacherRelMapper;

    @Mock
    private AttendanceMapper attendanceMapper;

    @Mock
    private TeachingClassMapper classMapper;

    private TimeSlotLessonRefServiceImpl service() {
        return new TimeSlotLessonRefServiceImpl(timeSlotMapper, lessonMapper, teacherRelMapper, attendanceMapper,
            classMapper);
    }

    private TimeSlotEntity timeSlot(Long id, Long institutionId) {
        TimeSlotEntity entity = new TimeSlotEntity();
        entity.setId(id);
        entity.setInstitutionId(institutionId);
        entity.setSlotCode("AM1");
        entity.setSlotName("上1");
        return entity;
    }

    private ScheduleLessonEntity lesson(Long id, Long classId, LocalDate date, boolean deleted) {
        ScheduleLessonEntity entity = new ScheduleLessonEntity();
        entity.setId(id);
        entity.setInstitutionId(1L);
        entity.setClassId(classId);
        entity.setTimeSlotId(9L);
        entity.setLessonDate(date);
        entity.setLessonWeekday(date.getDayOfWeek().getValue());
        entity.setStartTime(LocalTime.of(8, 0));
        entity.setEndTime(LocalTime.of(8, 45));
        entity.setLessonStatus("scheduled");
        entity.setDeleted(deleted);
        return entity;
    }

    private TeachingClassEntity teachingClass(Long id, String name) {
        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setId(id);
        entity.setClassName(name);
        return entity;
    }

    @Test
    void listRefsMarksTombstonesAndResolvesClassNames() {
        when(timeSlotMapper.selectById(9L)).thenReturn(timeSlot(9L, 1L));
        when(lessonMapper.selectRefsByTimeSlot(1L, 9L)).thenReturn(List.of(
            lesson(101L, 51L, LocalDate.of(2026, 9, 28), false),
            lesson(102L, 52L, LocalDate.of(2026, 9, 29), true)));
        // 52 号班级已被删除，批量查不回名称，视图留空由前端回显「班级 #52」
        when(classMapper.selectBatchIds(anyList())).thenReturn(List.of(teachingClass(51L, "三年级作文A班")));

        List<TimeSlotLessonRefView> views = service().listRefsByTimeSlot(1L, 9L);

        assertEquals(2, views.size());
        assertEquals("三年级作文A班", views.getFirst().className());
        assertTrue(!views.getFirst().deleted());
        assertEquals(102L, views.getLast().lessonId());
        assertTrue(views.getLast().deleted());
        assertNull(views.getLast().className());
    }

    @Test
    void listRefsRejectsAnotherInstitutionSlot() {
        when(timeSlotMapper.selectById(9L)).thenReturn(timeSlot(9L, 2L));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().listRefsByTimeSlot(1L, 9L));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
        verify(lessonMapper, never()).selectRefsByTimeSlot(1L, 9L);
    }

    @Test
    void listRefsFailsWhenSlotMissing() {
        when(timeSlotMapper.selectById(9L)).thenReturn(null);

        assertEquals(ResultCode.NOT_FOUND,
            assertThrows(BusinessException.class, () -> service().listRefsByTimeSlot(1L, 9L)).getResultCode());
    }

    @Test
    void purgeOnlyRemovesTombstonesWithoutLiveMakeupRefs() {
        when(timeSlotMapper.selectById(9L)).thenReturn(timeSlot(9L, 1L));
        when(lessonMapper.selectRefsByTimeSlot(1L, 9L)).thenReturn(List.of(
            lesson(101L, 51L, LocalDate.of(2026, 9, 28), false),
            lesson(102L, 51L, LocalDate.of(2026, 9, 29), true),
            lesson(103L, 52L, LocalDate.of(2026, 9, 30), true)));
        when(lessonMapper.countLiveMakeupRefs(102L)).thenReturn(0L);
        when(lessonMapper.countLiveMakeupRefs(103L)).thenReturn(1L);
        when(timeSlotMapper.countLiveLessonRefs(9L)).thenReturn(1L);

        TimeSlotLessonCleanupResponse response = service().purgeTombstoneLessons(1L, 9L);

        assertEquals(1, response.purgedCount());
        assertEquals(1, response.skippedCount());
        assertEquals(1, response.remainingLiveRefCount());
        verify(lessonMapper).deleteTombstonePhysically(102L);
        verify(attendanceMapper).physicalDeleteByLessonId(102L);
        verify(teacherRelMapper).physicalDeleteByLessonId(102L);
        // 未删除的课次与被补课引用的墓碑都不许碰
        verify(lessonMapper, never()).deleteTombstonePhysically(101L);
        verify(lessonMapper, never()).deleteTombstonePhysically(103L);
    }

    @Test
    void purgeWithoutTombstonesReportsRemainingLiveRefs() {
        when(timeSlotMapper.selectById(9L)).thenReturn(timeSlot(9L, 1L));
        when(lessonMapper.selectRefsByTimeSlot(1L, 9L)).thenReturn(List.of(
            lesson(101L, 51L, LocalDate.of(2026, 9, 28), false)));
        when(timeSlotMapper.countLiveLessonRefs(9L)).thenReturn(1L);

        TimeSlotLessonCleanupResponse response = service().purgeTombstoneLessons(1L, 9L);

        assertEquals(0, response.purgedCount());
        assertEquals(1, response.remainingLiveRefCount());
        verify(lessonMapper, never()).deleteTombstonePhysically(101L);
    }
}
