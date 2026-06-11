package com.jiaopei.platform.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ScheduleLessonEntity;
import com.jiaopei.platform.education.domain.entity.ScheduleTeacherRelEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.ScheduleLessonRequest;
import com.jiaopei.platform.education.mapper.ScheduleLessonMapper;
import com.jiaopei.platform.education.mapper.ScheduleTeacherRelMapper;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.impl.ScheduleLessonServiceImpl;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import com.jiaopei.platform.organization.mapper.ClassroomMapper;
import com.jiaopei.platform.organization.mapper.TimeSlotMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleLessonServiceImplTest {

    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final ScheduleTeacherRelMapper teacherRelMapper = mock(ScheduleTeacherRelMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
    private final TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
    private final ScheduleLessonServiceImpl service = new ScheduleLessonServiceImpl(
        lessonMapper,
        teacherRelMapper,
        classMapper,
        teacherMapper,
        classroomMapper,
        timeSlotMapper
    );

    @Test
    void createLessonWritesTeacherRelations() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));
        ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
        relation.setLessonId(900L);
        relation.setTeacherId(400L);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));

        assertEquals(List.of(400L), service.createLesson(request()).teacherIds());

        verify(teacherRelMapper).physicalDeleteByLessonId(900L);
        verify(teacherRelMapper).insert(any(ScheduleTeacherRelEntity.class));
    }

    @Test
    void createLessonRejectsClassConflict() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.createLesson(request()));
    }

    private void stubValidMaterial() {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(100L);
        teachingClass.setInstitutionId(1L);
        teachingClass.setCampusId(2L);
        teachingClass.setTermId(3L);
        when(classMapper.selectById(100L)).thenReturn(teachingClass);

        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(200L);
        classroom.setCampusId(2L);
        when(classroomMapper.selectById(200L)).thenReturn(classroom);

        TimeSlotEntity timeSlot = new TimeSlotEntity();
        timeSlot.setId(500L);
        timeSlot.setInstitutionId(1L);
        timeSlot.setCampusId(2L);
        timeSlot.setWeekday(1);
        timeSlot.setStartTime(LocalTime.of(9, 0));
        timeSlot.setEndTime(LocalTime.of(10, 0));
        when(timeSlotMapper.selectById(500L)).thenReturn(timeSlot);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(400L);
        teacher.setInstitutionId(1L);
        teacher.setCampusId(2L);
        when(teacherMapper.selectById(400L)).thenReturn(teacher);
    }

    private ScheduleLessonRequest request() {
        return new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "Math",
            "scheduled",
            List.of(400L),
            "test"
        );
    }
}
