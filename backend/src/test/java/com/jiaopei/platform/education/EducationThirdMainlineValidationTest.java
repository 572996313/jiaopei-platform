package com.jiaopei.platform.education;

import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.StudentCreateRequest;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.dto.TeachingClassCreateRequest;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.impl.StudentServiceImpl;
import com.jiaopei.platform.education.service.impl.TeacherServiceImpl;
import com.jiaopei.platform.education.service.impl.TeachingClassServiceImpl;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.mapper.TermMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationThirdMainlineValidationTest {

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private TermMapper termMapper;

    @Test
    void createTeacherRejectsDuplicateTeacherCode() {
        TeacherServiceImpl service = new TeacherServiceImpl(teacherMapper, institutionMapper, campusMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(teacherMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createTeacher(teacherRequest("T001")));

        verify(teacherMapper, never()).insert(any(TeacherEntity.class));
    }

    @Test
    void createStudentRejectsDuplicateStudentNo() {
        StudentServiceImpl service = new StudentServiceImpl(studentMapper, institutionMapper, campusMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(studentMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createStudent(studentRequest("S001", LocalDate.of(2018, 1, 1))));

        verify(studentMapper, never()).insert(any(StudentEntity.class));
    }

    @Test
    void createStudentRejectsFutureBirthday() {
        StudentServiceImpl service = new StudentServiceImpl(studentMapper, institutionMapper, campusMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());

        assertThrows(BusinessException.class, () -> service.createStudent(studentRequest("S002", LocalDate.now().plusDays(1))));

        verify(studentMapper, never()).insert(any(StudentEntity.class));
    }

    @Test
    void createClassRejectsCurrentStudentsOverCapacity() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());

        assertThrows(BusinessException.class, () -> service.createClass(classRequest("C001", "一班", 10, 11)));

        verify(teachingClassMapper, never()).insert(any(TeachingClassEntity.class));
    }

    @Test
    void createClassRejectsDuplicateClassNameInCampus() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());
        when(teachingClassMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThrows(BusinessException.class, () -> service.createClass(classRequest("C002", "一班", 30, 0)));

        verify(teachingClassMapper, never()).insert(any(TeachingClassEntity.class));
    }

    private TeacherCreateRequest teacherRequest(String teacherCode) {
        return new TeacherCreateRequest(
            1L,
            null,
            teacherCode,
            "张老师",
            "13800000000",
            null,
            null,
            null,
            null,
            LocalDate.now(),
            null
        );
    }

    private StudentCreateRequest studentRequest(String studentNo, LocalDate birthday) {
        return new StudentCreateRequest(
            1L,
            null,
            studentNo,
            "李同学",
            null,
            birthday,
            "李家长",
            "13900000000",
            null,
            LocalDate.now(),
            null
        );
    }

    private TeachingClassCreateRequest classRequest(String classCode, String className, int capacityLimit,
                                                    int currentStudents) {
        return new TeachingClassCreateRequest(
            1L,
            1L,
            null,
            classCode,
            className,
            null,
            capacityLimit,
            currentStudents,
            null,
            LocalDate.now(),
            null
        );
    }

    private CampusEntity campus() {
        CampusEntity campus = new CampusEntity();
        campus.setId(1L);
        campus.setInstitutionId(1L);
        return campus;
    }
}
