package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiaopei.platform.common.api.PageResult;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.dto.TeacherPageQuery;
import com.jiaopei.platform.education.dto.TeacherUpdateRequest;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.service.TeacherService;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.tenant.mapper.InstitutionMapper;
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
    public PageResult<TeacherEntity> pageTeachers(TeacherPageQuery query) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .orderByAsc(TeacherEntity::getInstitutionId)
            .orderByAsc(TeacherEntity::getCampusId)
            .orderByAsc(TeacherEntity::getTeacherCode);
        if (query.getInstitutionId() != null) {
            wrapper.eq(TeacherEntity::getInstitutionId, query.getInstitutionId());
        }
        if (query.getCampusId() != null) {
            wrapper.eq(TeacherEntity::getCampusId, query.getCampusId());
        }
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(TeacherEntity::getTeacherName, keyword)
                .or()
                .like(TeacherEntity::getTeacherCode, keyword)
                .or()
                .like(TeacherEntity::getPhone, keyword));
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(TeacherEntity::getStatus, status);
        }
        Page<TeacherEntity> page = teacherMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public TeacherEntity createTeacher(TeacherCreateRequest request) {
        requireInstitution(request.institutionId());
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), request.institutionId());
        }
        if (request.hiredAt() != null && request.hiredAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入职日期不能晚于当前日期");
        }

        String teacherCode = normalize(request.teacherCode());
        String teacherName = normalize(request.teacherName());
        String phone = normalizeToNull(request.phone());
        assertUniqueTeacherCode(request.institutionId(), teacherCode, null);
        if (phone != null) {
            assertUniqueTeacherPhone(request.institutionId(), phone, null);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师编码或手机号已存在：" + request.institutionId());
        }
        return entity;
    }

    @Override
    public TeacherEntity updateTeacher(Long id, TeacherUpdateRequest request) {
        TeacherEntity existing = requireTeacher(id);
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), existing.getInstitutionId());
        }
        if (request.hiredAt() != null && request.hiredAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入职日期不能晚于当前日期");
        }

        if (request.teacherCode() != null && !request.teacherCode().isBlank()) {
            String teacherCode = normalize(request.teacherCode());
            if (!Objects.equals(existing.getTeacherCode(), teacherCode)) {
                assertUniqueTeacherCode(existing.getInstitutionId(), teacherCode, id);
                existing.setTeacherCode(teacherCode);
            }
        }
        if (request.teacherName() != null && !request.teacherName().isBlank()) {
            existing.setTeacherName(normalize(request.teacherName()));
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            String phone = normalizeToNull(request.phone());
            if (!Objects.equals(existing.getPhone(), phone)) {
                assertUniqueTeacherPhone(existing.getInstitutionId(), phone, id);
                existing.setPhone(phone);
            }
        }
        existing.setCampusId(request.campusId());
        existing.setGender(normalizeToNull(request.gender()));
        existing.setTitle(normalizeToNull(request.title()));
        existing.setEmploymentType(request.employmentType() == null || request.employmentType().isBlank() ? existing.getEmploymentType() : request.employmentType());
        existing.setStatus(request.status() == null || request.status().isBlank() ? existing.getStatus() : request.status());
        existing.setHiredAt(request.hiredAt());
        existing.setRemark(request.remark());
        try {
            teacherMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师编码或手机号已存在：" + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteTeacher(Long id) {
        requireTeacher(id);
        teacherMapper.deleteById(id);
    }

    private TeacherEntity requireTeacher(Long id) {
        TeacherEntity entity = teacherMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教师不存在：" + id);
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

    private void assertUniqueTeacherCode(Long institutionId, String teacherCode, Long excludeId) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getInstitutionId, institutionId)
            .eq(TeacherEntity::getTeacherCode, teacherCode);
        if (excludeId != null) {
            wrapper.ne(TeacherEntity::getId, excludeId);
        }
        Long count = teacherMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师编码已存在：" + institutionId);
        }
    }

    private void assertUniqueTeacherPhone(Long institutionId, String phone, Long excludeId) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getInstitutionId, institutionId)
            .eq(TeacherEntity::getPhone, phone);
        if (excludeId != null) {
            wrapper.ne(TeacherEntity::getId, excludeId);
        }
        Long count = teacherMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师手机号已存在：" + institutionId);
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
