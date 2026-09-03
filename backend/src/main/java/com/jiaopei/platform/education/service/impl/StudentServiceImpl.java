package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.dto.StudentCreateRequest;
import com.jiaopei.platform.education.dto.StudentUpdateRequest;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.service.StudentService;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;

    public StudentServiceImpl(StudentMapper studentMapper, InstitutionMapper institutionMapper,
                              CampusMapper campusMapper) {
        this.studentMapper = studentMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
    }

    @Override
    public List<StudentEntity> listStudents(Long institutionId, Long campusId, String keyword) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
            .orderByAsc(StudentEntity::getInstitutionId)
            .orderByAsc(StudentEntity::getCampusId)
            .orderByAsc(StudentEntity::getStudentNo);
        if (institutionId != null) {
            wrapper.eq(StudentEntity::getInstitutionId, institutionId);
        }
        if (campusId != null) {
            wrapper.eq(StudentEntity::getCampusId, campusId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(StudentEntity::getStudentName, keyword)
                .or()
                .like(StudentEntity::getStudentNo, keyword));
        }
        return studentMapper.selectList(wrapper);
    }

    @Override
    public StudentEntity createStudent(StudentCreateRequest request) {
        requireInstitution(request.institutionId());
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), request.institutionId());
        }
        if (request.birthday() != null && request.birthday().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "birthday cannot be in the future");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "enrolledAt cannot be in the future");
        }

        String studentNo = normalize(request.studentNo());
        assertUniqueStudentNo(request.institutionId(), studentNo, null);

        StudentEntity entity = new StudentEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setStudentNo(studentNo);
        entity.setStudentName(normalize(request.studentName()));
        entity.setGender(normalizeToNull(request.gender()));
        entity.setBirthday(request.birthday());
        entity.setGuardianName(normalizeToNull(request.guardianName()));
        entity.setGuardianPhone(normalizeToNull(request.guardianPhone()));
        entity.setStatus(request.status() == null || request.status().isBlank() ? "studying" : request.status());
        entity.setEnrolledAt(request.enrolledAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            studentMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student studentNo already exists in institution: " + request.institutionId());
        }
        return entity;
    }

    @Override
    public StudentEntity updateStudent(Long id, StudentUpdateRequest request) {
        StudentEntity existing = requireStudent(id);
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), existing.getInstitutionId());
        }
        if (request.birthday() != null && request.birthday().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "birthday cannot be in the future");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "enrolledAt cannot be in the future");
        }

        if (request.studentNo() != null && !request.studentNo().isBlank()) {
            String studentNo = normalize(request.studentNo());
            if (!Objects.equals(existing.getStudentNo(), studentNo)) {
                assertUniqueStudentNo(existing.getInstitutionId(), studentNo, id);
                existing.setStudentNo(studentNo);
            }
        }
        if (request.studentName() != null && !request.studentName().isBlank()) {
            existing.setStudentName(normalize(request.studentName()));
        }
        existing.setCampusId(request.campusId());
        existing.setGender(normalizeToNull(request.gender()));
        existing.setBirthday(request.birthday());
        existing.setGuardianName(normalizeToNull(request.guardianName()));
        existing.setGuardianPhone(normalizeToNull(request.guardianPhone()));
        existing.setStatus(request.status() == null || request.status().isBlank() ? existing.getStatus() : request.status());
        existing.setEnrolledAt(request.enrolledAt());
        existing.setRemark(request.remark());
        try {
            studentMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student studentNo already exists in institution: " + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteStudent(Long id) {
        StudentEntity existing = requireStudent(id);
        existing.setDeleted(Boolean.TRUE);
        studentMapper.updateById(existing);
    }

    private StudentEntity requireStudent(Long id) {
        StudentEntity entity = studentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Student does not exist: " + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
    }

    private void requireCampusInInstitution(Long campusId, Long institutionId) {
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Campus does not exist: " + campusId);
        }
        if (!Objects.equals(campus.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Campus does not belong to institution: " + institutionId);
        }
    }

    private void assertUniqueStudentNo(Long institutionId, String studentNo, Long excludeId) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
            .eq(StudentEntity::getInstitutionId, institutionId)
            .eq(StudentEntity::getStudentNo, studentNo);
        if (excludeId != null) {
            wrapper.ne(StudentEntity::getId, excludeId);
        }
        Long count = studentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student studentNo already exists in institution: " + institutionId);
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
