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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
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
        // 新建学期是马上要排课、收台账的，默认启用；草稿/归档留给现场自己收
        entity.setStatus(request.status() == null || request.status().isBlank() ? "active" : request.status());
        entity.setDefaultTerm(request.defaultTerm() == null ? Boolean.FALSE : request.defaultTerm());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            termMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            // 先查后插仍有并发窗口，撞键时把原始约束名记进日志：只看业务提示分不清是编码还是名称
            log.warn("创建学期撞唯一键(编码={}, 名称={})：{}", termCode, termName,
                exception.getMostSpecificCause().getMessage());
            throw duplicateKeyException(exception, termCode, termName);
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
            log.warn("更新学期撞唯一键(id={}, 编码={}, 名称={})：{}", id, existing.getTermCode(),
                existing.getTermName(), exception.getMostSpecificCause().getMessage());
            throw duplicateKeyException(exception, existing.getTermCode(), existing.getTermName());
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

    /**
     * 把唯一键冲突翻成操作员看得懂的提示：按撞到的约束名区分编码/名称，并带上冲突的那个值。
     *
     * <p>预检走 MyBatis-Plus 会自动补 {@code deleted = 0}，而唯一键自 V103 起也只约束未删除行，
     * 两边口径一致；这里只兜并发窗口，认不出约束名时把两个值都报出来，免得只报编码误导排查方向。</p>
     */
    private BusinessException duplicateKeyException(DuplicateKeyException exception, String termCode, String termName) {
        String detail = exception.getMostSpecificCause().getMessage();
        String raw = detail == null ? "" : detail;
        if (raw.contains("uk_jp_term_name")) {
            return new BusinessException(ResultCode.BAD_REQUEST, "该机构下已存在学期名称：" + termName);
        }
        if (raw.contains("uk_jp_term_code")) {
            return new BusinessException(ResultCode.BAD_REQUEST, "该机构下已存在学期编码：" + termCode);
        }
        return new BusinessException(ResultCode.BAD_REQUEST,
            "该机构下学期编码 " + termCode + " 或名称 " + termName + " 已存在");
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
