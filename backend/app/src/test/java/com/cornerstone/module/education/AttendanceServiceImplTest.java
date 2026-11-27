package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.attendance.dto.AttendanceBatchItemRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.attendance.service.impl.AttendanceServiceImpl;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 课次点名的存盘口径，重点是"留空即撤销点名"这条分支。
 */
class AttendanceServiceImplTest {

    private static final Long LESSON_ID = 900L;
    private static final Long CLASS_ID = 800L;
    private static final Long INSTITUTION_ID = 100L;
    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 9, 21);

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AttendanceEntity.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudentEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleTeacherRelEntity.class);
        TableInfoHelper.initTableInfo(assistant, TeacherEntity.class);
    }

    private final AttendanceMapper attendanceMapper = mock(AttendanceMapper.class);
    private final ClassStudentMapper classStudentMapper = mock(ClassStudentMapper.class);
    private final TeachingClassMapper teachingClassMapper = mock(TeachingClassMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final ScheduleLessonMapper scheduleLessonMapper = mock(ScheduleLessonMapper.class);
    private final ScheduleTeacherRelMapper scheduleTeacherRelMapper = mock(ScheduleTeacherRelMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final AttendanceServiceImpl service = new AttendanceServiceImpl(
        attendanceMapper, classStudentMapper, teachingClassMapper, studentMapper, scheduleLessonMapper,
        scheduleTeacherRelMapper, teacherMapper);

    /** 模拟库里的考勤行：删除与查询都走这份列表，才能验到"撤销后页面不再显示该学员状态"。 */
    private final List<AttendanceEntity> storedAttendances = new ArrayList<>();

    @BeforeEach
    void setUpLesson() {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setId(LESSON_ID);
        lesson.setInstitutionId(INSTITUTION_ID);
        lesson.setClassId(CLASS_ID);
        lesson.setLessonDate(LESSON_DATE);
        lesson.setLessonStatus("scheduled");
        lesson.setLessonTitle("专题课");
        when(scheduleLessonMapper.selectById(LESSON_ID)).thenReturn(lesson);

        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(CLASS_ID);
        teachingClass.setInstitutionId(INSTITUTION_ID);
        teachingClass.setClassName("E2A 测试班");
        when(teachingClassMapper.selectById(CLASS_ID)).thenReturn(teachingClass);

        when(attendanceMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(storedAttendances));
        // 新建也要落进这份列表：页面视角是存完再重查一遍，insert 只当空 mock 的话「点上了几节」永远是 0
        when(attendanceMapper.insert(any(AttendanceEntity.class))).thenAnswer(invocation -> {
            storedAttendances.add(invocation.getArgument(0));
            return 1;
        });
        when(attendanceMapper.physicalDeleteById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            storedAttendances.removeIf(item -> item.getId().equals(id));
            return 1;
        });
        // 存盘口径的用例只关心点名结果，统一按考勤管理码登录，把"仅限本人课次"的分支让给下面的专用例
        authenticate(Set.of("education:read", "education:attendance:write", "education:attendance:manage"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherWithoutManagePermissionCannotMarkLessonOfOthers() {
        authenticate(Set.of("education:read", "education:attendance:write"));
        when(teacherMapper.selectOne(any())).thenReturn(teacher(500L, 7L, "王老师"));
        when(scheduleTeacherRelMapper.selectCount(any())).thenReturn(0L);
        inClassStudents(1L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.saveLessonAttendance(request(item(1L, "present"))));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
        assertEquals("只能为本人授课的课次点名，本课次的授课教师里没有「王老师」", exception.getMessage());
        verify(attendanceMapper, never()).insert(any(AttendanceEntity.class));
    }

    @Test
    void teacherCanMarkOwnLesson() {
        authenticate(Set.of("education:read", "education:attendance:write"));
        when(teacherMapper.selectOne(any())).thenReturn(teacher(500L, 7L, "王老师"));
        when(scheduleTeacherRelMapper.selectCount(any())).thenReturn(1L);
        inClassStudents(1L);

        AttendanceLessonPageView page = service.saveLessonAttendance(request(item(1L, "present")));

        assertEquals(1, page.summary().markedCount());
    }

    @Test
    void accountWithoutTeacherProfileCannotMarkEvenWithAttendanceWrite() {
        authenticate(Set.of("education:read", "education:attendance:write"));
        when(teacherMapper.selectOne(any())).thenReturn(null);
        inClassStudents(1L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.saveLessonAttendance(request(item(1L, "present"))));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
        assertEquals("当前账号未绑定本机构的教师档案，无法点名；请先绑定教师档案或授予考勤管理权限", exception.getMessage());
        verify(attendanceMapper, never()).insert(any(AttendanceEntity.class));
    }

    private void authenticate(Set<String> permissions) {
        AuthenticatedUser user = new AuthenticatedUser(7L, INSTITUTION_ID, "teacher01",
            Set.of("TEACHER"), permissions, false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    private TeacherEntity teacher(Long teacherId, Long userId, String teacherName) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(teacherId);
        teacher.setInstitutionId(INSTITUTION_ID);
        teacher.setUserId(userId);
        teacher.setTeacherName(teacherName);
        teacher.setDeleted(Boolean.FALSE);
        return teacher;
    }

    @Test
    void blankStatusRevokesExistingAttendance() {
        inClassStudents(1L, 2L);
        storedAttendances.add(attendance(11L, 1L, "present"));
        storedAttendances.add(attendance(12L, 2L, "absent"));

        AttendanceLessonPageView page = service.saveLessonAttendance(request(
            item(1L, ""),
            item(2L, "leave")));

        verify(attendanceMapper).physicalDeleteById(11L);
        verify(attendanceMapper, never()).insert(any(AttendanceEntity.class));
        assertEquals(1, storedAttendances.size());
        assertEquals("leave", storedAttendances.getFirst().getAttendanceStatus());
        assertEquals(1, page.summary().markedCount());
        assertNull(page.students().getFirst().attendanceId());
        assertEquals("leave", page.students().get(1).attendanceStatus());
    }

    @Test
    void blankStatusWithoutExistingAttendanceIsNoop() {
        inClassStudents(1L);

        AttendanceLessonPageView page = service.saveLessonAttendance(request(item(1L, null)));

        verify(attendanceMapper, never()).physicalDeleteById(any());
        verify(attendanceMapper, never()).insert(any(AttendanceEntity.class));
        verify(attendanceMapper, never()).updateById(any(AttendanceEntity.class));
        assertEquals(0, page.summary().markedCount());
    }

    @Test
    void revokingLeftOverStudentDoesNotRequireActiveEnrollment() {
        // 学员课后转班/退班也要能撤销误点，撤销不查在班
        inClassStudents(2L);
        storedAttendances.add(attendance(13L, 3L, "present"));

        AttendanceLessonPageView page = service.saveLessonAttendance(request(item(3L, "  ")));

        verify(attendanceMapper).physicalDeleteById(13L);
        assertEquals(0, storedAttendances.size());
        assertEquals(0, page.summary().markedCount());
    }

    @Test
    void unknownStatusIsRejectedWithAllowedValues() {
        inClassStudents(1L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.saveLessonAttendance(request(item(1L, "here"))));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        assertEquals("不支持的考勤状态：here，可选值：present、absent、leave、late", exception.getMessage());
        verify(attendanceMapper, never()).insert(any(AttendanceEntity.class));
    }

    private void inClassStudents(Long... studentIds) {
        List<ClassStudentEntity> students = new ArrayList<>();
        for (Long studentId : studentIds) {
            ClassStudentEntity classStudent = new ClassStudentEntity();
            classStudent.setInstitutionId(INSTITUTION_ID);
            classStudent.setClassId(CLASS_ID);
            classStudent.setStudentId(studentId);
            classStudent.setStatus("active");
            students.add(classStudent);
        }
        when(classStudentMapper.selectList(any())).thenReturn(students);
    }

    private AttendanceEntity attendance(Long id, Long studentId, String status) {
        AttendanceEntity entity = new AttendanceEntity();
        entity.setId(id);
        entity.setInstitutionId(INSTITUTION_ID);
        entity.setClassId(CLASS_ID);
        entity.setLessonId(LESSON_ID);
        entity.setStudentId(studentId);
        entity.setAttendanceDate(LESSON_DATE);
        entity.setAttendanceStatus(status);
        entity.setSource("manual");
        entity.setDeleted(Boolean.FALSE);
        return entity;
    }

    private AttendanceBatchItemRequest item(Long studentId, String status) {
        return new AttendanceBatchItemRequest(studentId, status, "manual", null);
    }

    private AttendanceLessonBatchSaveRequest request(AttendanceBatchItemRequest... items) {
        return new AttendanceLessonBatchSaveRequest(LESSON_ID, List.of(items));
    }
}
