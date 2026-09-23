package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.dto.StudentCreateRequest;
import com.cornerstone.module.education.dto.StudentPageQuery;
import com.cornerstone.module.education.dto.StudentUpdateRequest;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.service.StudentService;
import com.cornerstone.module.education.service.support.GenderDictValidator;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
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
    private final GenderDictValidator genderDictValidator;

    public StudentServiceImpl(StudentMapper studentMapper, InstitutionMapper institutionMapper,
                              CampusMapper campusMapper, GenderDictValidator genderDictValidator) {
        this.studentMapper = studentMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
        this.genderDictValidator = genderDictValidator;
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
    public PageResult<StudentEntity> pageStudents(StudentPageQuery query) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
            .orderByAsc(StudentEntity::getInstitutionId)
            .orderByAsc(StudentEntity::getCampusId)
            .orderByAsc(StudentEntity::getStudentNo);
        if (query.getInstitutionId() != null) {
            wrapper.eq(StudentEntity::getInstitutionId, query.getInstitutionId());
        }
        if (query.getCampusId() != null) {
            wrapper.eq(StudentEntity::getCampusId, query.getCampusId());
        }
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(StudentEntity::getStudentName, keyword)
                .or()
                .like(StudentEntity::getStudentNo, keyword));
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(StudentEntity::getStatus, status);
        }
        Page<StudentEntity> page = studentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public StudentEntity createStudent(StudentCreateRequest request) {
        requireInstitution(request.institutionId());
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), request.institutionId());
        }
        if (request.birthday() != null && request.birthday().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "出生日期不能晚于当前日期");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入学日期不能晚于当前日期");
        }

        String studentNo = normalize(request.studentNo());
        assertUniqueStudentNo(request.institutionId(), studentNo, null);

        StudentEntity entity = new StudentEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setStudentNo(studentNo);
        entity.setStudentName(normalize(request.studentName()));
        entity.setGender(genderDictValidator.normalizeAndValidate(request.gender()));
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学员编号已存在：" + request.institutionId());
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "出生日期不能晚于当前日期");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入学日期不能晚于当前日期");
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
        existing.setGender(genderDictValidator.normalizeAndValidate(request.gender()));
        existing.setBirthday(request.birthday());
        existing.setGuardianName(normalizeToNull(request.guardianName()));
        existing.setGuardianPhone(normalizeToNull(request.guardianPhone()));
        existing.setStatus(request.status() == null || request.status().isBlank() ? existing.getStatus() : request.status());
        existing.setEnrolledAt(request.enrolledAt());
        existing.setRemark(request.remark());
        try {
            studentMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学员编号已存在：" + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteStudent(Long id) {
        requireStudent(id);
        studentMapper.deleteById(id);
    }

    private StudentEntity requireStudent(Long id) {
        StudentEntity entity = studentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private void requireCampusInInstitution(Long campusId, Long institutionId) {
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + campusId);
        }
        if (!Objects.equals(campus.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "校区不属于该机构：" + institutionId);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学员编号已存在：" + institutionId);
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
