package com.cornerstone.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.dto.TermCreateRequest;
import com.cornerstone.module.organization.dto.TermUpdateRequest;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.service.TermService;
import com.cornerstone.module.organization.service.support.DictValueValidator;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TermServiceImpl implements TermService {

    private final TermMapper termMapper;
    private final InstitutionMapper institutionMapper;
    private final DictValueValidator dictValueValidator;
    private final CodeRuleService codeRuleService;

    public TermServiceImpl(TermMapper termMapper, InstitutionMapper institutionMapper,
                           DictValueValidator dictValueValidator, CodeRuleService codeRuleService) {
        this.termMapper = termMapper;
        this.institutionMapper = institutionMapper;
        this.dictValueValidator = dictValueValidator;
        this.codeRuleService = codeRuleService;
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
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + request.institutionId());
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (request.enrollmentStartDate() != null && request.enrollmentEndDate() != null
            && request.enrollmentStartDate().isAfter(request.enrollmentEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报名开始日期不能晚于报名结束日期");
        }
        String termCode = codeRuleService.resolveForCreate("term", request.termCode());
        String termName = normalize(request.termName());
        assertUniqueTermCode(request.institutionId(), termCode, null);
        assertUniqueTermName(request.institutionId(), termName, null);

        TermEntity entity = new TermEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setTermCode(termCode);
        entity.setTermName(termName);
        entity.setAcademicYear(normalize(request.academicYear()));
        entity.setSeason(dictValueValidator.validate("term_season", "学季", request.season()));
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学期编码或学期名称已存在：" + termCode);
        }
        return entity;
    }

    @Override
    public TermEntity updateTerm(Long id, TermUpdateRequest request) {
        TermEntity existing = requireTerm(id);
        boolean hasDates = request.startDate() != null && request.endDate() != null;
        boolean onlyEndDate = request.startDate() == null && request.endDate() != null;
        boolean onlyStartDate = request.startDate() != null && request.endDate() == null;
        if (hasDates && request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (onlyEndDate && request.endDate().isBefore(existing.getStartDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (onlyStartDate && request.startDate().isAfter(existing.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        boolean hasEnrollmentDates = request.enrollmentStartDate() != null && request.enrollmentEndDate() != null;
        boolean onlyEnrollmentEnd = request.enrollmentStartDate() == null && request.enrollmentEndDate() != null;
        boolean onlyEnrollmentStart = request.enrollmentStartDate() != null && request.enrollmentEndDate() == null;
        if (hasEnrollmentDates && request.enrollmentStartDate().isAfter(request.enrollmentEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报名开始日期不能晚于报名结束日期");
        }
        if (onlyEnrollmentEnd && existing.getEnrollmentStartDate() != null
            && request.enrollmentEndDate().isBefore(existing.getEnrollmentStartDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报名开始日期不能晚于报名结束日期");
        }
        if (onlyEnrollmentStart && existing.getEnrollmentEndDate() != null
            && request.enrollmentStartDate().isAfter(existing.getEnrollmentEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报名开始日期不能晚于报名结束日期");
        }
        if (request.termCode() != null && !request.termCode().isBlank()) {
            String termCode = normalize(request.termCode());
            if (!Objects.equals(existing.getTermCode(), termCode)) {
                assertUniqueTermCode(existing.getInstitutionId(), termCode, id);
                existing.setTermCode(termCode);
            }
        }
        if (request.termName() != null && !request.termName().isBlank()) {
            String termName = normalize(request.termName());
            if (!Objects.equals(existing.getTermName(), termName)) {
                assertUniqueTermName(existing.getInstitutionId(), termName, id);
                existing.setTermName(termName);
            }
        }
        if (request.academicYear() != null && !request.academicYear().isBlank()) {
            existing.setAcademicYear(normalize(request.academicYear()));
        }
        if (request.season() != null) {
            existing.setSeason(dictValueValidator.validate("term_season", "学季", request.season()));
        }
        if (request.startDate() != null) {
            existing.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            existing.setEndDate(request.endDate());
        }
        if (request.enrollmentStartDate() != null) {
            existing.setEnrollmentStartDate(request.enrollmentStartDate());
        }
        if (request.enrollmentEndDate() != null) {
            existing.setEnrollmentEndDate(request.enrollmentEndDate());
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.defaultTerm() != null) {
            existing.setDefaultTerm(request.defaultTerm());
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            termMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学期编码或学期名称已存在：" + existing.getTermCode());
        }
        return existing;
    }

    @Override
    public void deleteTerm(Long id) {
        requireTerm(id);
        termMapper.deleteById(id);
    }

    private TermEntity requireTerm(Long id) {
        TermEntity entity = termMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在：" + id);
        }
        return entity;
    }

    private void assertUniqueTermCode(Long institutionId, String termCode, Long excludeId) {
        LambdaQueryWrapper<TermEntity> wrapper = new LambdaQueryWrapper<TermEntity>()
            .eq(TermEntity::getInstitutionId, institutionId)
            .eq(TermEntity::getTermCode, termCode);
        if (excludeId != null) {
            wrapper.ne(TermEntity::getId, excludeId);
        }
        Long count = termMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学期编码已存在：" + termCode);
        }
    }

    private void assertUniqueTermName(Long institutionId, String termName, Long excludeId) {
        LambdaQueryWrapper<TermEntity> wrapper = new LambdaQueryWrapper<TermEntity>()
            .eq(TermEntity::getInstitutionId, institutionId)
            .eq(TermEntity::getTermName, termName);
        if (excludeId != null) {
            wrapper.ne(TermEntity::getId, excludeId);
        }
        Long count = termMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学期名称已存在：" + termName);
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
