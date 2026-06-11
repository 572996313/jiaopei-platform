package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.EnrollmentEntity;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.EnrollmentCreateRequest;
import com.jiaopei.platform.education.mapper.EnrollmentMapper;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.EnrollmentService;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentMapper enrollmentMapper;
    private final InstitutionMapper institutionMapper;
    private final StudentMapper studentMapper;
    private final TeachingClassMapper teachingClassMapper;

    public EnrollmentServiceImpl(EnrollmentMapper enrollmentMapper, InstitutionMapper institutionMapper,
                                 StudentMapper studentMapper, TeachingClassMapper teachingClassMapper) {
        this.enrollmentMapper = enrollmentMapper;
        this.institutionMapper = institutionMapper;
        this.studentMapper = studentMapper;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    public List<EnrollmentEntity> listEnrollments(Long institutionId, Long classId, Long studentId) {
        LambdaQueryWrapper<EnrollmentEntity> wrapper = new LambdaQueryWrapper<EnrollmentEntity>()
            .orderByAsc(EnrollmentEntity::getInstitutionId)
            .orderByAsc(EnrollmentEntity::getClassId)
            .orderByAsc(EnrollmentEntity::getStudentId);
        if (institutionId != null) {
            wrapper.eq(EnrollmentEntity::getInstitutionId, institutionId);
        }
        if (classId != null) {
            wrapper.eq(EnrollmentEntity::getClassId, classId);
        }
        if (studentId != null) {
            wrapper.eq(EnrollmentEntity::getStudentId, studentId);
        }
        return enrollmentMapper.selectList(wrapper);
    }

    @Override
    public EnrollmentEntity createEnrollment(EnrollmentCreateRequest request) {
        requireInstitution(request.institutionId());
        StudentEntity student = requireStudentInInstitution(request.studentId(), request.institutionId());
        TeachingClassEntity teachingClass = requireClassInInstitution(request.classId(), request.institutionId());
        if (student.getCampusId() != null && !Objects.equals(student.getCampusId(), teachingClass.getCampusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student campus does not match class campus");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "enrolledAt cannot be in the future");
        }

        String enrollmentNo = normalize(request.enrollmentNo());
        assertUniqueEnrollmentNo(request.institutionId(), enrollmentNo);
        assertUniqueStudentClass(request.studentId(), request.classId());

        EnrollmentEntity entity = new EnrollmentEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setStudentId(request.studentId());
        entity.setClassId(request.classId());
        entity.setEnrollmentNo(enrollmentNo);
        entity.setChannel(normalizeToNull(request.channel()));
        entity.setStatus(request.status() == null || request.status().isBlank() ? "submitted" : request.status());
        entity.setEnrolledAt(request.enrolledAt() == null ? LocalDate.now() : request.enrolledAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            enrollmentMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Enrollment already exists");
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
    }

    private StudentEntity requireStudentInInstitution(Long studentId, Long institutionId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Student does not exist: " + studentId);
        }
        if (!Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student does not belong to institution: " + institutionId);
        }
        return student;
    }

    private TeachingClassEntity requireClassInInstitution(Long classId, Long institutionId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Class does not exist: " + classId);
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class does not belong to institution: " + institutionId);
        }
        return teachingClass;
    }

    private void assertUniqueEnrollmentNo(Long institutionId, String enrollmentNo) {
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<EnrollmentEntity>()
            .eq(EnrollmentEntity::getInstitutionId, institutionId)
            .eq(EnrollmentEntity::getEnrollmentNo, enrollmentNo));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Enrollment enrollmentNo already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueStudentClass(Long studentId, Long classId) {
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<EnrollmentEntity>()
            .eq(EnrollmentEntity::getStudentId, studentId)
            .eq(EnrollmentEntity::getClassId, classId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student already has an enrollment for class: " + classId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
