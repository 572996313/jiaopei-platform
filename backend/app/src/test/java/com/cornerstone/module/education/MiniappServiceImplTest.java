package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.module.education.domain.entity.UserStudentRelEntity;
import com.cornerstone.module.education.dto.BindStudentRequest;
import com.cornerstone.module.education.dto.StudentBindSelfRequest;
import com.cornerstone.module.education.dto.StudentBriefView;
import com.cornerstone.module.education.dto.StudentProfileSaveRequest;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.service.impl.MiniappServiceImpl;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.StudentParentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.StudentParentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
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
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final SystemDictService systemDictService = mock(SystemDictService.class);
    private final DictValueValidator dictValueValidator = new DictValueValidator(systemDictService);

    private MiniappServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MiniappServiceImpl(relMapper, studentMapper, parentMapper, classStudentMapper,
            classMapper, lessonMapper, courseMapper, classroomMapper, teacherMapper, systemConfigService, dictValueValidator);
        when(systemConfigService.getIntConfig("auth.register.default_institution_id", 1)).thenReturn(1);
        when(systemDictService.listItems("gender")).thenReturn(List.of(
            new DictItemView(40L, 5L, "male", "男", "male", 10, "active", null),
            new DictItemView(41L, 5L, "female", "女", "female", 20, "active", null),
            new DictItemView(42L, 5L, "unknown", "未知", "unknown", 30, "active", null)));
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

        StudentBriefView result = service.bindStudent(new BindStudentRequest("STUDENT_NO", "S001", "小明", "老大"));

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
            () -> service.bindStudent(new BindStudentRequest("STUDENT_NO", "S001", "小明", "老大")));

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

        StudentBriefView result = service.bindStudent(new BindStudentRequest("PARENT_PHONE", "13800000000", "小明", null));

        assertEquals("小明", result.studentName());
    }

    @Test
    void bindStudentRejectsUnknownPhone() {
        when(parentMapper.selectList(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.bindStudent(new BindStudentRequest("PARENT_PHONE", "13800000000", "小明", null)));

        assertTrue(exception.getMessage().contains("未找到"));
    }

    @Test
    void bindStudentRejectsNameMismatchByStudentNo() {
        StudentEntity student = new StudentEntity();
        student.setId(10L);
        student.setInstitutionId(1L);
        student.setStudentNo("S001");
        student.setStudentName("小明");
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(relMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.bindStudent(new BindStudentRequest("STUDENT_NO", "S001", "小红", "老大")));

        assertTrue(exception.getMessage().contains("不匹配"));
        verify(relMapper, never()).insert(any(UserStudentRelEntity.class));
    }

    @Test
    void bindStudentRejectsNameMismatchByParentPhone() {
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

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.bindStudent(new BindStudentRequest("PARENT_PHONE", "13800000000", "小红", null)));

        assertTrue(exception.getMessage().contains("不匹配"));
        verify(relMapper, never()).insert(any(UserStudentRelEntity.class));
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

    @Test
    void unbindStudentPhysicallyDeletesRelation() {
        when(relMapper.physicalDelete(1L, 10L)).thenReturn(1);

        service.unbindStudent(10L);

        verify(relMapper).physicalDelete(1L, 10L);
    }

    @Test
    void unbindStudentRejectsWhenNotBound() {
        when(relMapper.physicalDelete(1L, 10L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.unbindStudent(10L));

        assertTrue(exception.getMessage().contains("未找到该学员的绑定关系"));
    }

    @Test
    void myStudentProfileReturnsUnboundWhenNoOwnedStudent() {
        authenticateStudent(2L);
        when(studentMapper.selectOne(any())).thenReturn(null);

        var profile = service.myStudentProfile();

        assertEquals(Boolean.FALSE, profile.bound());
        assertTrue(profile.studentId() == null);
    }

    @Test
    void myStudentProfileReturnsOwnedStudent() {
        authenticateStudent(2L);
        when(studentMapper.selectOne(any())).thenReturn(student(10L, "S001", "小明"));
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        var profile = service.myStudentProfile();

        assertEquals(Boolean.TRUE, profile.bound());
        assertEquals("小明", profile.studentName());
        assertEquals("S001", profile.studentNo());
    }

    @Test
    void bindSelfStudentRejectsNonStudentRole() {
        // 默认家长身份调用学生档案方法应被拒
        authenticate(1L);
        when(studentMapper.selectOne(any())).thenReturn(student(10L, "S001", "小明"));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.bindSelfStudent(new StudentBindSelfRequest("S001", "小明")));

        assertTrue(exception.getMessage().contains("仅学生账号"));
    }

    @Test
    void bindSelfStudentBindsOwnedStudentAndCreatesRelation() {
        authenticateStudent(2L);
        StudentEntity student = student(10L, "S001", "小明");
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(relMapper.selectCount(any())).thenReturn(0L);
        when(relMapper.insert(any(UserStudentRelEntity.class))).thenReturn(1);
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        var profile = service.bindSelfStudent(new StudentBindSelfRequest("S001", "小明"));

        assertEquals(Boolean.TRUE, profile.bound());
        // 学生账号 2L 绑定档案 10L,归属写入账号 id
        assertEquals(Long.valueOf(2L), student.getUserId());
        verify(studentMapper).updateById(student);
        verify(relMapper).insert(any(UserStudentRelEntity.class));
    }

    @Test
    void bindSelfStudentRejectsNameMismatchOrAlreadyOwned() {
        authenticateStudent(2L);
        // 姓名不匹配
        when(studentMapper.selectOne(any())).thenReturn(student(10L, "S001", "小明"));

        BusinessException mismatch = assertThrows(BusinessException.class,
            () -> service.bindSelfStudent(new StudentBindSelfRequest("S001", "小红")));
        assertTrue(mismatch.getMessage().contains("不匹配"));

        // 该档案已被他人归属
        StudentEntity owned = student(10L, "S001", "小明");
        owned.setUserId(99L);
        when(studentMapper.selectOne(any())).thenReturn(owned);
        BusinessException taken = assertThrows(BusinessException.class,
            () -> service.bindSelfStudent(new StudentBindSelfRequest("S001", "小明")));
        assertTrue(taken.getMessage().contains("已归属"));
    }

    @Test
    void bindSelfStudentIsIdempotentForSelfOwned() {
        authenticateStudent(2L);
        StudentEntity student = student(10L, "S001", "小明");
        student.setUserId(2L);
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(relMapper.selectCount(any())).thenReturn(1L);
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        var profile = service.bindSelfStudent(new StudentBindSelfRequest("S001", "小明"));

        assertEquals(Boolean.TRUE, profile.bound());
        verify(studentMapper, never()).updateById(student);
    }

    @Test
    void saveStudentProfileCreatesNewStudentAndOwnsIt() {
        authenticateStudent(2L);
        when(studentMapper.selectOne(any())).thenReturn(null);
        when(studentMapper.insert(any(StudentEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, StudentEntity.class).setId(30L);
            return 1;
        });
        when(relMapper.selectCount(any())).thenReturn(0L);
        when(relMapper.insert(any(UserStudentRelEntity.class))).thenReturn(1);
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        var profile = service.saveStudentProfile(new StudentProfileSaveRequest("小花", "female", null));

        assertEquals(Boolean.TRUE, profile.bound());
        assertEquals("小花", profile.studentName());
        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentMapper).insert(captor.capture());
        assertEquals(2L, captor.getValue().getUserId());
        assertEquals("female", captor.getValue().getGender());
        assertTrue(captor.getValue().getStudentNo() != null && captor.getValue().getStudentNo().startsWith("S"));
    }

    @Test
    void saveStudentProfileUpdatesOwnedStudentOnlyEditableFields() {
        authenticateStudent(2L);
        StudentEntity student = student(10L, "S001", "小明");
        student.setUserId(2L);
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        var profile = service.saveStudentProfile(new StudentProfileSaveRequest("小明改", "male", null));

        assertEquals("小明改", profile.studentName());
        assertEquals("male", profile.gender());
        verify(studentMapper).updateById(student);
    }

    @Test
    void saveStudentProfileRejectsGenderOutsideDict() {
        authenticateStudent(2L);
        // 历史大写值不再受理：写入口径统一为 gender 字典的 item_value
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.saveStudentProfile(new StudentProfileSaveRequest("小明", "MALE", null)));
        assertTrue(error.getMessage().contains("性别取值无效"));
        verify(studentMapper, never()).updateById(any(StudentEntity.class));
    }

    // ---- helpers ----

    private void authenticate(Long userId) {
        AuthenticatedUser user = new AuthenticatedUser(
            userId, 1L, "wx_openid", java.util.Set.of("MINIAPP_PARENT"), java.util.Set.of("miniapp:access"), false, "parent");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", java.util.Set.of()));
    }

    private void authenticateStudent(Long userId) {
        AuthenticatedUser user = new AuthenticatedUser(
            userId, 1L, "wx_openid", java.util.Set.of("MINIAPP_STUDENT"), java.util.Set.of("miniapp:access"), false, "student");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", java.util.Set.of()));
    }

    private StudentEntity student(Long id, String studentNo, String studentName) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setInstitutionId(1L);
        student.setStudentNo(studentNo);
        student.setStudentName(studentName);
        return student;
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