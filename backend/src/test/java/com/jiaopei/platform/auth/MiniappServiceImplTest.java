package com.jiaopei.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.domain.UserStudentRelEntity;
import com.jiaopei.platform.auth.dto.BindStudentRequest;
import com.jiaopei.platform.auth.dto.StudentBriefView;
import com.jiaopei.platform.auth.mapper.UserStudentRelMapper;
import com.jiaopei.platform.auth.service.impl.MiniappServiceImpl;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.domain.entity.ScheduleLessonEntity;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.StudentParentEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.mapper.ClassStudentMapper;
import com.jiaopei.platform.education.mapper.CourseMapper;
import com.jiaopei.platform.education.mapper.ScheduleLessonMapper;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.StudentParentMapper;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.mapper.ClassroomMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MiniappServiceImplTest {

    private final UserStudentRelMapper relMapper = mock(UserStudentRelMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final StudentParentMapper parentMapper = mock(StudentParentMapper.class);
    private final ClassStudentMapper classStudentMapper = mock(ClassStudentMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);

    private MiniappServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MiniappServiceImpl(relMapper, studentMapper, parentMapper, classStudentMapper,
            classMapper, lessonMapper, courseMapper, classroomMapper, teacherMapper);
        authenticate(1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bindStudentByStudentNo() {
        StudentEntity student = new StudentEntity();
        student.setId(10L);
        student.setInstitutionId(1L);
        student.setStudentNo("S001");
        student.setStudentName("小明");
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(relMapper.selectCount(any())).thenReturn(0L);
        when(relMapper.insert(any(UserStudentRelEntity.class))).thenReturn(1);
        when(classStudentMapper.selectList(any())).thenReturn(List.of());
        // currentClassName 会查一次班级为空
        when(classMapper.selectById(any())).thenReturn(null);

        StudentBriefView result = service.bindStudent(new BindStudentRequest("STUDENT_NO", "S001", "老大"));

        assertEquals("小明", result.studentName());
        ArgumentCaptor<UserStudentRelEntity> captor = ArgumentCaptor.forClass(UserStudentRelEntity.class);
        verify(relMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(10L, captor.getValue().getStudentId());
    }

    @Test
    void bindStudentRejectsDuplicate() {
        StudentEntity student = new StudentEntity();
        student.setId(10L);
        student.setInstitutionId(1L);
        student.setStudentNo("S001");
        student.setStudentName("小明");
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(relMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.bindStudent(new BindStudentRequest("STUDENT_NO", "S001", "老大")));

        assertTrue(exception.getMessage().contains("已绑定"));
        verify(relMapper, never()).insert(any(UserStudentRelEntity.class));
    }

    @Test
    void bindStudentByParentPhoneResolvesFirstStudent() {
        StudentParentEntity parent = new StudentParentEntity();
        parent.setStudentId(10L);
        when(parentMapper.selectList(any())).thenReturn(List.of(parent));
        StudentEntity student = new StudentEntity();
        student.setId(10L);
        student.setInstitutionId(1L);
        student.setStudentNo("S001");
        student.setStudentName("小明");
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(relMapper.selectCount(any())).thenReturn(0L);
        when(relMapper.insert(any(UserStudentRelEntity.class))).thenReturn(1);
        when(classStudentMapper.selectList(any())).thenReturn(List.of());
        when(classMapper.selectById(any())).thenReturn(null);

        StudentBriefView result = service.bindStudent(new BindStudentRequest("PARENT_PHONE", "13800000000", null));

        assertEquals("小明", result.studentName());
    }

    @Test
    void bindStudentRejectsUnknownPhone() {
        when(parentMapper.selectList(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.bindStudent(new BindStudentRequest("PARENT_PHONE", "13800000000", null)));

        assertTrue(exception.getMessage().contains("未找到"));
    }

    @Test
    void studentLessonsFiltersByBoundStudentAndSetsClassNames() {
        when(relMapper.selectCount(any())).thenReturn(1L);

        ClassStudentEntity cls = new ClassStudentEntity();
        cls.setStudentId(10L);
        cls.setClassId(20L);
        cls.setStatus("ACTIVE");
        when(classStudentMapper.selectList(any())).thenReturn(List.of(cls));

        TeachingClassEntity clazz = new TeachingClassEntity();
        clazz.setId(20L);
        clazz.setClassName("三(1)班");
        clazz.setCourseId(30L);
        clazz.setTeacherId(40L);
        when(classMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(clazz));

        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setId(1L);
        lesson.setClassId(20L);
        lesson.setClassroomId(50L);
        lesson.setLessonDate(LocalDate.now());
        lesson.setLessonWeekday(1);
        lesson.setStartTime(LocalTime.of(9, 0));
        lesson.setEndTime(LocalTime.of(10, 0));
        lesson.setLessonTitle("第1课");
        lesson.setLessonStatus("scheduled");
        when(lessonMapper.selectList(any())).thenReturn(List.of(lesson));

        when(classroomMapper.selectBatchIds(List.of(50L))).thenReturn(List.of(classroom()));
        when(teacherMapper.selectBatchIds(List.of(40L))).thenReturn(List.of(teacher()));

        var result = service.studentLessons(10L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        assertEquals(1, result.size());
        assertEquals("三(1)班", result.get(0).className());
        assertEquals("张老师", result.get(0).teacherName());
        assertEquals("13800001111", result.get(0).teacherPhone());
    }

    @Test
    void studentLessonsRejectsUnboundStudent() {
        when(relMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.studentLessons(10L, LocalDate.now(), LocalDate.now()));

        assertTrue(exception.getMessage().contains("无权查看"));
    }

    // ---- helpers ----

    private void authenticate(Long userId) {
        AuthenticatedUser user = new AuthenticatedUser(
            userId, 1L, "wx_openid", java.util.Set.of("MINIAPP_PARENT"), java.util.Set.of("miniapp:access"), false);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", java.util.Set.of()));
    }

    private ClassroomEntity classroom() {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(50L);
        classroom.setClassroomName("A-101");
        return classroom;
    }

    private TeacherEntity teacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(40L);
        teacher.setTeacherName("张老师");
        teacher.setPhone("13800001111");
        return teacher;
    }
}