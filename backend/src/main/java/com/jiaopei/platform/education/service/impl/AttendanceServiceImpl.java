package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.AttendanceEntity;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.AttendanceCreateRequest;
import com.jiaopei.platform.education.mapper.AttendanceMapper;
import com.jiaopei.platform.education.mapper.ClassStudentMapper;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.AttendanceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceMapper attendanceMapper;
    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final StudentMapper studentMapper;

    public AttendanceServiceImpl(AttendanceMapper attendanceMapper, ClassStudentMapper classStudentMapper,
                                 TeachingClassMapper teachingClassMapper, StudentMapper studentMapper) {
        this.attendanceMapper = attendanceMapper;
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.studentMapper = studentMapper;
    }

    @Override
    public List<AttendanceEntity> listAttendances(Long classId, Long studentId, LocalDate attendanceDate) {
        LambdaQueryWrapper<AttendanceEntity> wrapper = new LambdaQueryWrapper<AttendanceEntity>()
            .orderByDesc(AttendanceEntity::getAttendanceDate)
            .orderByAsc(AttendanceEntity::getClassId)
            .orderByAsc(AttendanceEntity::getStudentId);
        if (classId != null) {
            wrapper.eq(AttendanceEntity::getClassId, classId);
        }
        if (studentId != null) {
            wrapper.eq(AttendanceEntity::getStudentId, studentId);
        }
        if (attendanceDate != null) {
            wrapper.eq(AttendanceEntity::getAttendanceDate, attendanceDate);
        }
        return attendanceMapper.selectList(wrapper);
    }

    @Override
    public AttendanceEntity createAttendance(AttendanceCreateRequest request) {
        TeachingClassEntity teachingClass = requireClass(request.classId());
        StudentEntity student = requireStudent(request.studentId());
        Long institutionId = request.institutionId() == null ? teachingClass.getInstitutionId() : request.institutionId();
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId) || !Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class and student must belong to institution: " + institutionId);
        }
        requireActiveClassStudent(request.classId(), request.studentId());
        assertUniqueAttendance(request.classId(), request.studentId(), request.attendanceDate());

        AttendanceEntity entity = new AttendanceEntity();
        entity.setInstitutionId(institutionId);
        entity.setClassId(request.classId());
        entity.setStudentId(request.studentId());
        entity.setAttendanceDate(request.attendanceDate());
        entity.setAttendanceStatus(request.attendanceStatus() == null || request.attendanceStatus().isBlank() ? "present" : request.attendanceStatus());
        entity.setSource(request.source() == null || request.source().isBlank() ? "manual" : request.source());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            attendanceMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Attendance already exists for student/date");
        }
        return entity;
    }

    private TeachingClassEntity requireClass(Long classId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Class does not exist: " + classId);
        }
        return teachingClass;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Student does not exist: " + studentId);
        }
        return student;
    }

    private void requireActiveClassStudent(Long classId, Long studentId) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStudentId, studentId)
            .eq(ClassStudentEntity::getStatus, "active"));
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student is not active in class: " + classId);
        }
    }

    private void assertUniqueAttendance(Long classId, Long studentId, LocalDate attendanceDate) {
        Long count = attendanceMapper.selectCount(new LambdaQueryWrapper<AttendanceEntity>()
            .eq(AttendanceEntity::getClassId, classId)
            .eq(AttendanceEntity::getStudentId, studentId)
            .eq(AttendanceEntity::getAttendanceDate, attendanceDate));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Attendance already exists for student/date");
        }
    }
}
