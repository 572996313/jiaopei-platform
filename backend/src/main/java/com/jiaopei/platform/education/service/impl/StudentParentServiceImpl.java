package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.StudentParentEntity;
import com.jiaopei.platform.education.dto.StudentParentCreateRequest;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.StudentParentMapper;
import com.jiaopei.platform.education.service.StudentParentService;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class StudentParentServiceImpl implements StudentParentService {

    private final StudentParentMapper studentParentMapper;
    private final StudentMapper studentMapper;
    private final InstitutionMapper institutionMapper;

    public StudentParentServiceImpl(StudentParentMapper studentParentMapper, StudentMapper studentMapper,
                                    InstitutionMapper institutionMapper) {
        this.studentParentMapper = studentParentMapper;
        this.studentMapper = studentMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<StudentParentEntity> listStudentParents(Long institutionId, Long studentId) {
        LambdaQueryWrapper<StudentParentEntity> wrapper = new LambdaQueryWrapper<StudentParentEntity>()
            .orderByAsc(StudentParentEntity::getStudentId)
            .orderByDesc(StudentParentEntity::getIsPrimary)
            .orderByAsc(StudentParentEntity::getId);
        if (institutionId != null) {
            wrapper.eq(StudentParentEntity::getInstitutionId, institutionId);
        }
        if (studentId != null) {
            wrapper.eq(StudentParentEntity::getStudentId, studentId);
        }
        return studentParentMapper.selectList(wrapper);
    }

    @Override
    public StudentParentEntity createStudentParent(StudentParentCreateRequest request) {
        return saveStudentParent(null, request);
    }

    @Override
    public StudentParentEntity updateStudentParent(Long id, StudentParentCreateRequest request) {
        requireStudentParent(id);
        return saveStudentParent(id, request);
    }

    @Override
    public void deleteStudentParent(Long id) {
        StudentParentEntity entity = requireStudentParent(id);
        StudentEntity student = studentMapper.selectById(entity.getStudentId());
        studentParentMapper.deleteById(id);
        if (student != null) {
            StudentParentEntity currentPrimary = syncPrimaryParent(student.getId());
            syncStudentGuardian(student, currentPrimary);
        }
    }

    private StudentParentEntity saveStudentParent(Long id, StudentParentCreateRequest request) {
        requireInstitution(request.institutionId());
        StudentEntity student = requireStudentInInstitution(request.studentId(), request.institutionId());

        String parentPhone = normalizeToNull(request.parentPhone());
        if (parentPhone != null) {
            assertUniqueParentPhone(request.studentId(), parentPhone, id);
        }
        if (Boolean.TRUE.equals(request.isPrimary())) {
            demoteOtherPrimaryParents(request.studentId(), id);
        }

        StudentParentEntity entity = id == null ? new StudentParentEntity() : requireStudentParent(id);
        entity.setId(id);
        entity.setInstitutionId(request.institutionId());
        entity.setStudentId(request.studentId());
        entity.setParentName(request.parentName().trim());
        entity.setRelationType(normalizeToNull(request.relationType()));
        entity.setParentPhone(parentPhone);
        entity.setWechatNo(normalizeToNull(request.wechatNo()));
        entity.setIsPrimary(Boolean.TRUE.equals(request.isPrimary()));
        entity.setIsEmergencyContact(Boolean.TRUE.equals(request.isEmergencyContact()));
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setRemark(request.remark());
        if (id == null) {
            entity.setDeleted(Boolean.FALSE);
            try {
                studentParentMapper.insert(entity);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "Student parent phone already exists for student: " + request.studentId());
            }
        } else {
            studentParentMapper.updateById(entity);
        }
        StudentParentEntity currentPrimary = syncPrimaryParent(student.getId());
        syncStudentGuardian(student, currentPrimary);
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

    private void assertUniqueParentPhone(Long studentId, String parentPhone, Long excludeId) {
        LambdaQueryWrapper<StudentParentEntity> wrapper = new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getParentPhone, parentPhone);
        if (excludeId != null) {
            wrapper.ne(StudentParentEntity::getId, excludeId);
        }
        Long count = studentParentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Student parent phone already exists for student: " + studentId);
        }
    }

    private void demoteOtherPrimaryParents(Long studentId, Long excludeId) {
        LambdaQueryWrapper<StudentParentEntity> wrapper = new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getIsPrimary, Boolean.TRUE);
        if (excludeId != null) {
            wrapper.ne(StudentParentEntity::getId, excludeId);
        }
        List<StudentParentEntity> existingPrimaryParents = studentParentMapper.selectList(wrapper);
        for (StudentParentEntity existingPrimaryParent : existingPrimaryParents) {
            existingPrimaryParent.setIsPrimary(Boolean.FALSE);
            studentParentMapper.updateById(existingPrimaryParent);
        }
    }

    private StudentParentEntity syncPrimaryParent(Long studentId) {
        StudentParentEntity primaryParent = studentParentMapper.selectOne(new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getIsPrimary, Boolean.TRUE)
            .last("LIMIT 1"));
        if (primaryParent != null) {
            return primaryParent;
        }

        StudentParentEntity fallbackParent = studentParentMapper.selectOne(new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getStatus, "active")
            .orderByAsc(StudentParentEntity::getId)
            .last("LIMIT 1"));
        if (fallbackParent != null) {
            fallbackParent.setIsPrimary(Boolean.TRUE);
            studentParentMapper.updateById(fallbackParent);
        }
        return fallbackParent;
    }

    private void syncStudentGuardian(StudentEntity student, StudentParentEntity currentPrimary) {
        String guardianName = currentPrimary == null ? null : currentPrimary.getParentName();
        String guardianPhone = currentPrimary == null ? null : currentPrimary.getParentPhone();
        if (!Objects.equals(student.getGuardianName(), guardianName)
            || !Objects.equals(student.getGuardianPhone(), guardianPhone)) {
            student.setGuardianName(guardianName);
            student.setGuardianPhone(guardianPhone);
            studentMapper.updateById(student);
        }
    }

    private StudentParentEntity requireStudentParent(Long id) {
        StudentParentEntity entity = studentParentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Student parent does not exist: " + id);
        }
        return entity;
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
