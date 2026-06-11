package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.domain.entity.TermEntity;
import com.jiaopei.platform.organization.dto.TermCreateRequest;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.mapper.TermMapper;
import com.jiaopei.platform.organization.service.TermService;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TermServiceImpl implements TermService {

    private final TermMapper termMapper;
    private final InstitutionMapper institutionMapper;

    public TermServiceImpl(TermMapper termMapper, InstitutionMapper institutionMapper) {
        this.termMapper = termMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<TermEntity> listTerms(Long institutionId) {
        LambdaQueryWrapper<TermEntity> wrapper = new LambdaQueryWrapper<TermEntity>()
            .orderByAsc(TermEntity::getInstitutionId)
            .orderByDesc(TermEntity::getAcademicYear)
            .orderByAsc(TermEntity::getStartDate);
        if (institutionId != null) {
            wrapper.eq(TermEntity::getInstitutionId, institutionId);
        }
        return termMapper.selectList(wrapper);
    }

    @Override
    public TermEntity createTerm(TermCreateRequest request) {
        InstitutionEntity institution = institutionMapper.selectById(request.institutionId());
        if (institution == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + request.institutionId());
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "startDate must be earlier than or equal to endDate");
        }
        if (request.enrollmentStartDate() != null && request.enrollmentEndDate() != null
            && request.enrollmentStartDate().isAfter(request.enrollmentEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "enrollmentStartDate must be earlier than or equal to enrollmentEndDate");
        }
        String termCode = normalize(request.termCode());
        String termName = normalize(request.termName());
        assertUniqueTermCode(request.institutionId(), termCode);
        assertUniqueTermName(request.institutionId(), termName);

        TermEntity entity = new TermEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setTermCode(termCode);
        entity.setTermName(termName);
        entity.setAcademicYear(normalize(request.academicYear()));
        entity.setSeason(request.season());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setEnrollmentStartDate(request.enrollmentStartDate());
        entity.setEnrollmentEndDate(request.enrollmentEndDate());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "draft" : request.status());
        entity.setDefaultTerm(request.defaultTerm() == null ? Boolean.FALSE : request.defaultTerm());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            termMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Term termCode or termName already exists in institution: " + request.institutionId());
        }
        return entity;
    }

    private void assertUniqueTermCode(Long institutionId, String termCode) {
        Long count = termMapper.selectCount(new LambdaQueryWrapper<TermEntity>()
            .eq(TermEntity::getInstitutionId, institutionId)
            .eq(TermEntity::getTermCode, termCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Term termCode already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueTermName(Long institutionId, String termName) {
        Long count = termMapper.selectCount(new LambdaQueryWrapper<TermEntity>()
            .eq(TermEntity::getInstitutionId, institutionId)
            .eq(TermEntity::getTermName, termName));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Term termName already exists in institution: " + institutionId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
