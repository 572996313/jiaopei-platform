package com.cornerstone.module.education;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.education.domain.entity.EnrollmentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.dto.EnrollmentCreateRequest;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.EnrollmentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ClassStudentService;
import com.cornerstone.module.education.service.impl.EnrollmentServiceImpl;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.support.CodeRuleTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 报名即入班:报名创建时同事务写入班级名册的联动行为 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private ClassStudentService classStudentService;

    private final SystemDictService systemDictService = mock(SystemDictService.class);
    private final DictValueValidator dictValueValidator = new DictValueValidator(systemDictService);

    @Test
    void createEnrollmentWritesClassRosterWithEnrolledDate() {
        EnrollmentServiceImpl service = service();
        stubMainline();
        when(classStudentMapper.selectCount(any())).thenReturn(0L);

        service.createEnrollment(enrollmentRequest());

        ArgumentCaptor<ClassStudentCreateRequest> captor = ArgumentCaptor.forClass(ClassStudentCreateRequest.class);
        verify(classStudentService).createClassStudent(captor.capture());
        ClassStudentCreateRequest rosterRequest = captor.getValue();
        assertEquals(1L, rosterRequest.institutionId());
        assertEquals(20L, rosterRequest.classId());
        assertEquals(10L, rosterRequest.studentId());
        assertEquals(LocalDate.of(2026, 6, 1), rosterRequest.joinedAt());
        verify(enrollmentMapper).insert(any(EnrollmentEntity.class));
    }

    @Test
    void createEnrollmentSkipsRosterWhenStudentAlreadyInClass() {
        EnrollmentServiceImpl service = service();
        stubMainline();
        when(classStudentMapper.selectCount(any())).thenReturn(1L);

        service.createEnrollment(enrollmentRequest());

        // 名册已有该学员(经「分班与名册」补录先入了班)时只补报名台账,不重复入班
        verify(classStudentService, never()).createClassStudent(any());
        verify(enrollmentMapper).insert(any(EnrollmentEntity.class));
    }

    @Test
    void createEnrollmentFailsWhenClassIsFull() {
        EnrollmentServiceImpl service = service();
        stubMainline();
        when(classStudentMapper.selectCount(any())).thenReturn(0L);
        when(classStudentService.createClassStudent(any()))
            .thenThrow(new BusinessException(ResultCode.BAD_REQUEST, "班级容量已满：20"));

        assertThrows(BusinessException.class, () -> service.createEnrollment(enrollmentRequest()));

        // 报名与入班在同一事务,入班抛异常后由事务整体回滚,这里只断言异常向外传播
        verify(enrollmentMapper).insert(any(EnrollmentEntity.class));
    }

    private EnrollmentServiceImpl service() {
        return new EnrollmentServiceImpl(enrollmentMapper, institutionMapper, studentMapper,
            teachingClassMapper, dictValueValidator, CodeRuleTestSupport.unconfiguredRules(),
            classStudentMapper, classStudentService);
    }

    private void stubMainline() {
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(studentMapper.selectById(10L)).thenReturn(student());
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 12));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(systemDictService.listItems("enrollment_channel")).thenReturn(List.of(
            new DictItemView(null, null, "front_desk", "前台报名", "front_desk", 10, "active", null)));
    }

    private EnrollmentCreateRequest enrollmentRequest() {
        return new EnrollmentCreateRequest(1L, 10L, 20L, "ENR-001", "front_desk", null, LocalDate.of(2026, 6, 1), null);
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
}
