package com.cornerstone.module.education;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.EnrollmentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.education.dto.AttendanceBatchItemRequest;
import com.cornerstone.module.education.dto.AttendanceCreateRequest;
import com.cornerstone.module.education.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.dto.EnrollmentCreateRequest;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.EnrollmentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.impl.AttendanceServiceImpl;
import com.cornerstone.module.education.service.impl.ClassStudentServiceImpl;
import com.cornerstone.module.education.service.impl.EnrollmentServiceImpl;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.support.CodeRuleTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class EducationFourthMainlineValidationTest {

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private AttendanceMapper attendanceMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private ScheduleLessonMapper scheduleLessonMapper;

    @Mock
    private ScheduleTeacherRelMapper scheduleTeacherRelMapper;

    @Mock
    private TeacherMapper teacherMapper;

    private final SystemDictService systemDictService = mock(SystemDictService.class);
    private final DictValueValidator dictValueValidator = new DictValueValidator(systemDictService);

    @BeforeEach
    void authenticateAsAttendanceManager() {
        // 点名保存现在会判数据范围，本类只验参数校验与异常映射，统一按考勤管理码登录
        AuthenticatedUser user = new AuthenticatedUser(7L, 1L, "admin",
            Set.of("ADMIN"), Set.of("education:read", "education:attendance:write", "education:attendance:manage"),
            false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createEnrollmentRejectsDuplicateStudentClass() {
        EnrollmentServiceImpl service = new EnrollmentServiceImpl(
            enrollmentMapper,
            institutionMapper,
            studentMapper,
            teachingClassMapper,
            dictValueValidator,
            CodeRuleTestSupport.unconfiguredRules()
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(studentMapper.selectById(10L)).thenReturn(student());
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 0));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L, 1L);
        when(systemDictService.listItems("enrollment_channel")).thenReturn(List.of(
            new DictItemView(null, null, "front_desk", "前台报名", "front_desk", 10, "active", null)));

        assertThrows(BusinessException.class, () -> service.createEnrollment(enrollmentRequest()));

        verify(enrollmentMapper, never()).insert(any(EnrollmentEntity.class));
    }

    @Test
    void createClassStudentRejectsFullClass() {
        ClassStudentServiceImpl service = new ClassStudentServiceImpl(classStudentMapper, teachingClassMapper, studentMapper);
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(1, 1));
        when(studentMapper.selectById(10L)).thenReturn(student());
        when(classStudentMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThrows(BusinessException.class, () -> service.createClassStudent(classStudentRequest()));

        verify(classStudentMapper, never()).insert(any(ClassStudentEntity.class));
    }

    @Test
    void createClassStudentRecalculatesCurrentStudentsFromActiveRelations() {
        ClassStudentServiceImpl service = new ClassStudentServiceImpl(classStudentMapper, teachingClassMapper, studentMapper);
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 9));
        when(studentMapper.selectById(10L)).thenReturn(student());
        when(classStudentMapper.selectCount(any())).thenReturn(0L, 2L, 3L);

        service.createClassStudent(classStudentRequest());

        ArgumentCaptor<TeachingClassEntity> captor = ArgumentCaptor.forClass(TeachingClassEntity.class);
        verify(teachingClassMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getCurrentStudents());
    }

    @Test
    void deleteClassStudentRecalculatesCurrentStudents() {
        ClassStudentServiceImpl service = new ClassStudentServiceImpl(classStudentMapper, teachingClassMapper, studentMapper);
        ClassStudentEntity relation = new ClassStudentEntity();
        relation.setId(500L);
        relation.setClassId(20L);
        relation.setStudentId(10L);
        relation.setStatus("active");
        when(classStudentMapper.selectById(500L)).thenReturn(relation);
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 5));
        when(classStudentMapper.selectCount(any())).thenReturn(4L);

        service.deleteClassStudent(500L);

        verify(classStudentMapper).deletePhysicallyById(500L);
        ArgumentCaptor<TeachingClassEntity> captor = ArgumentCaptor.forClass(TeachingClassEntity.class);
        verify(teachingClassMapper).updateById(captor.capture());
        assertEquals(4, captor.getValue().getCurrentStudents());
    }

    @Test
    void deleteClassStudentRejectsMissingRelation() {
        ClassStudentServiceImpl service = new ClassStudentServiceImpl(classStudentMapper, teachingClassMapper, studentMapper);
        when(classStudentMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.deleteClassStudent(999L));

        verify(classStudentMapper, never()).deletePhysicallyById(999L);
        verify(teachingClassMapper, never()).updateById(any(TeachingClassEntity.class));
    }

    @Test
    void createAttendanceRejectsStudentNotActiveInClass() {
        AttendanceServiceImpl service = new AttendanceServiceImpl(
            attendanceMapper,
            classStudentMapper,
            teachingClassMapper,
            studentMapper,
            scheduleLessonMapper,
            scheduleTeacherRelMapper,
            teacherMapper
        );
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 0));
        when(studentMapper.selectById(10L)).thenReturn(student());
        when(classStudentMapper.selectCount(any())).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service.createAttendance(attendanceRequest()));

        verify(attendanceMapper, never()).insert(any(AttendanceEntity.class));
    }

    @Test
    void createAttendanceRejectsUnsupportedStatus() {
        AttendanceServiceImpl service = new AttendanceServiceImpl(
            attendanceMapper,
            classStudentMapper,
            teachingClassMapper,
            studentMapper,
            scheduleLessonMapper,
            scheduleTeacherRelMapper,
            teacherMapper
        );
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 0));
        when(studentMapper.selectById(10L)).thenReturn(student());
        when(classStudentMapper.selectCount(any())).thenReturn(1L);
        when(attendanceMapper.selectCount(any())).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service.createAttendance(
            new AttendanceCreateRequest(1L, null, 20L, 10L, LocalDate.now(), "unknown", null, null)
        ));

        verify(attendanceMapper, never()).insert(any(AttendanceEntity.class));
    }

    @Test
    void saveLessonAttendanceMapsDuplicateKeyToBusinessException() {
        AttendanceServiceImpl service = new AttendanceServiceImpl(
            attendanceMapper,
            classStudentMapper,
            teachingClassMapper,
            studentMapper,
            scheduleLessonMapper,
            scheduleTeacherRelMapper,
            teacherMapper
        );
        when(scheduleLessonMapper.selectById(300L)).thenReturn(scheduleLesson());
        when(classStudentMapper.selectList(any())).thenReturn(List.of(activeClassStudent()));
        when(attendanceMapper.selectList(any())).thenReturn(List.of());
        when(attendanceMapper.insert(any(AttendanceEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(BusinessException.class, () -> service.saveLessonAttendance(
            new AttendanceLessonBatchSaveRequest(
                300L,
                List.of(new AttendanceBatchItemRequest(10L, "late", "manual", "traffic"))
            )
        ));
    }

    @Test
    void saveLessonAttendanceAllowsMakeupLesson() {
        AttendanceServiceImpl service = new AttendanceServiceImpl(
            attendanceMapper,
            classStudentMapper,
            teachingClassMapper,
            studentMapper,
            scheduleLessonMapper,
            scheduleTeacherRelMapper,
            teacherMapper
        );
        ScheduleLessonEntity makeupLesson = scheduleLesson();
        makeupLesson.setLessonStatus("makeup");
        when(scheduleLessonMapper.selectById(300L)).thenReturn(makeupLesson);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(activeClassStudent()));
        when(attendanceMapper.selectList(any())).thenReturn(List.of());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(student()));
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 0));

        service.saveLessonAttendance(
            new AttendanceLessonBatchSaveRequest(
                300L,
                List.of(new AttendanceBatchItemRequest(10L, "present", "manual", null))
            )
        );

        verify(attendanceMapper).insert(any(AttendanceEntity.class));
    }

    @Test
    void getLessonAttendancePageRejectsCancelledLesson() {
        AttendanceServiceImpl service = new AttendanceServiceImpl(
            attendanceMapper,
            classStudentMapper,
            teachingClassMapper,
            studentMapper,
            scheduleLessonMapper,
            scheduleTeacherRelMapper,
            teacherMapper
        );
        ScheduleLessonEntity cancelledLesson = scheduleLesson();
        cancelledLesson.setLessonStatus("cancelled");
        when(scheduleLessonMapper.selectById(300L)).thenReturn(cancelledLesson);

        assertThrows(BusinessException.class, () -> service.getLessonAttendancePage(300L));

        verify(attendanceMapper, never()).selectList(any());
    }

    private EnrollmentCreateRequest enrollmentRequest() {
        return new EnrollmentCreateRequest(1L, 10L, 20L, "ENR-001", "front_desk", null, LocalDate.now(), null);
    }

    private ClassStudentCreateRequest classStudentRequest() {
        return new ClassStudentCreateRequest(1L, 20L, 10L, null, LocalDate.now(), null, null);
    }

    private AttendanceCreateRequest attendanceRequest() {
        return new AttendanceCreateRequest(1L, null, 20L, 10L, LocalDate.now(), "present", null, null);
    }

    private StudentEntity student() {
        StudentEntity student = new StudentEntity();
        student.setId(10L);
        student.setInstitutionId(1L);
        return student;
    }

    private TeachingClassEntity teachingClass(int capacityLimit, int currentStudents) {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(20L);
        teachingClass.setInstitutionId(1L);
        teachingClass.setCampusId(2L);
        teachingClass.setCapacityLimit(capacityLimit);
        teachingClass.setCurrentStudents(currentStudents);
        return teachingClass;
    }

    private ClassStudentEntity activeClassStudent() {
        ClassStudentEntity entity = new ClassStudentEntity();
        entity.setClassId(20L);
        entity.setStudentId(10L);
        entity.setStatus("active");
        entity.setJoinedAt(LocalDate.of(2026, 6, 1));
        return entity;
    }

    private ScheduleLessonEntity scheduleLesson() {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setId(300L);
        lesson.setInstitutionId(1L);
        lesson.setCampusId(2L);
        lesson.setClassId(20L);
        lesson.setLessonDate(LocalDate.of(2026, 6, 8));
        lesson.setLessonStatus("scheduled");
        return lesson;
    }
}
