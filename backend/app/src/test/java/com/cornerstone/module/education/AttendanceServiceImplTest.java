package com.cornerstone.module.education;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.AttendanceBatchItemRequest;
import com.cornerstone.module.education.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.mapper.AttendanceMapper;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.impl.AttendanceServiceImpl;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    }

    private final AttendanceMapper attendanceMapper = mock(AttendanceMapper.class);
    private final ClassStudentMapper classStudentMapper = mock(ClassStudentMapper.class);
    private final TeachingClassMapper teachingClassMapper = mock(TeachingClassMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final ScheduleLessonMapper scheduleLessonMapper = mock(ScheduleLessonMapper.class);
    private final AttendanceServiceImpl service = new AttendanceServiceImpl(
        attendanceMapper, classStudentMapper, teachingClassMapper, studentMapper, scheduleLessonMapper);

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
        when(attendanceMapper.physicalDeleteById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            storedAttendances.removeIf(item -> item.getId().equals(id));
            return 1;
        });
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
