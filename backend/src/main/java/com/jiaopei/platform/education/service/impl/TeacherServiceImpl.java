package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.service.TeacherService;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TeacherServiceImpl implements TeacherService {

    private final TeacherMapper teacherMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;

    public TeacherServiceImpl(TeacherMapper teacherMapper, InstitutionMapper institutionMapper,
                              CampusMapper campusMapper) {
        this.teacherMapper = teacherMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
    }

    @Override
    public List<TeacherEntity> listTeachers(Long institutionId, Long campusId) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .orderByAsc(TeacherEntity::getInstitutionId)
            .orderByAsc(TeacherEntity::getCampusId)
            .orderByAsc(TeacherEntity::getTeacherCode);
        if (institutionId != null) {
            wrapper.eq(TeacherEntity::getInstitutionId, institutionId);
        }
        if (campusId != null) {
            wrapper.eq(TeacherEntity::getCampusId, campusId);
        }
        return teacherMapper.selectList(wrapper);
    }

    @Override
    public TeacherEntity createTeacher(TeacherCreateRequest request) {
        requireInstitution(request.institutionId());
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), request.institutionId());
        }
        if (request.hiredAt() != null && request.hiredAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "hiredAt cannot be in the future");
        }

        String teacherCode = normalize(request.teacherCode());
        String teacherName = normalize(request.teacherName());
        String phone = normalizeToNull(request.phone());
        assertUniqueTeacherCode(request.institutionId(), teacherCode);
        if (phone != null) {
            assertUniqueTeacherPhone(request.institutionId(), phone);
        }

        TeacherEntity entity = new TeacherEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTeacherCode(teacherCode);
        entity.setTeacherName(teacherName);
        entity.setPhone(phone);
        entity.setGender(normalizeToNull(request.gender()));
        entity.setTitle(normalizeToNull(request.title()));
        entity.setEmploymentType(request.employmentType() == null || request.employmentType().isBlank() ? "full_time" : request.employmentType());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "active" : request.status());
        entity.setHiredAt(request.hiredAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            teacherMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Teacher teacherCode or phone already exists in institution: " + request.institutionId());
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

    private void assertUniqueTeacherCode(Long institutionId, String teacherCode) {
        Long count = teacherMapper.selectCount(new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getInstitutionId, institutionId)
            .eq(TeacherEntity::getTeacherCode, teacherCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Teacher teacherCode already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueTeacherPhone(Long institutionId, String phone) {
        Long count = teacherMapper.selectCount(new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getInstitutionId, institutionId)
            .eq(TeacherEntity::getPhone, phone));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Teacher phone already exists in institution: " + institutionId);
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
