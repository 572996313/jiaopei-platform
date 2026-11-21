package com.cornerstone.module.education.fee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePriceEntity;
import com.cornerstone.module.education.fee.dto.FeePriceCreateRequest;
import com.cornerstone.module.education.fee.dto.FeePriceUpdateRequest;
import com.cornerstone.module.education.fee.mapper.FeeItemMapper;
import com.cornerstone.module.education.fee.mapper.FeePriceMapper;
import com.cornerstone.module.education.fee.service.FeePriceService;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class FeePriceServiceImpl implements FeePriceService {

    private static final Set<String> STATUSES = Set.of("active", "disabled");

    private final FeePriceMapper feePriceMapper;
    private final FeeItemMapper feeItemMapper;

    public FeePriceServiceImpl(FeePriceMapper feePriceMapper, FeeItemMapper feeItemMapper) {
        this.feePriceMapper = feePriceMapper;
        this.feeItemMapper = feeItemMapper;
    }

    @Override
    public List<FeePriceEntity> listPrices(Long institutionId, Long feeItemId, Long campusId) {
        Long resolved = InstitutionScopeGuard.resolveForQuery(institutionId, "收费价目");
        LambdaQueryWrapper<FeePriceEntity> wrapper = new LambdaQueryWrapper<>();
        if (resolved != null) {
            wrapper.eq(FeePriceEntity::getInstitutionId, resolved);
        }
        if (feeItemId != null) {
            wrapper.eq(FeePriceEntity::getFeeItemId, feeItemId);
        }
        if (campusId != null) {
            CampusScopeGuard.assertCampus(campusId, "收费价目");
            wrapper.eq(FeePriceEntity::getCampusId, campusId);
        }
        // 0 = 全校区价目，对所有受限角色可见
        CampusScopeGuard.applyWithAllSentinel(wrapper, FeePriceEntity::getCampusId, 0L);
        wrapper.orderByAsc(FeePriceEntity::getFeeItemId)
            .orderByAsc(FeePriceEntity::getCampusId)
            .orderByAsc(FeePriceEntity::getClassId)
            .orderByAsc(FeePriceEntity::getTermId)
            .orderByAsc(FeePriceEntity::getStudentId)
            .orderByAsc(FeePriceEntity::getId);
        return feePriceMapper.selectList(wrapper);
    }

    @Override
    public FeePriceEntity createPrice(FeePriceCreateRequest request) {
        Long institutionId = requireInstitution(request.institutionId());
        requireItem(request.feeItemId(), institutionId);
        long campusId = request.campusOrAny();
        long classId = request.classOrAny();
        long termId = request.termOrAny();
        long studentId = request.studentOrAny();
        assertCampusWritable(campusId);
        assertAtLeastOnePrice(request.monthlyPrice(), request.dailyPrice());
        assertUniqueScope(institutionId, request.feeItemId(), campusId, classId, termId, studentId, null);

        FeePriceEntity entity = new FeePriceEntity();
        entity.setInstitutionId(institutionId);
        entity.setFeeItemId(request.feeItemId());
        entity.setCampusId(campusId);
        entity.setClassId(classId);
        entity.setTermId(termId);
        entity.setStudentId(studentId);
        entity.setMonthlyPrice(scale(request.monthlyPrice()));
        entity.setDailyPrice(scale(request.dailyPrice()));
        entity.setStatus(requireStatus(request.status()));
        entity.setRemark(normalizeToNull(request.remark()));
        entity.setDeleted(Boolean.FALSE);
        try {
            feePriceMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "同一组价目维度已存在（校区/班级/学期/学员不能重复）");
        }
        return entity;
    }

    @Override
    public FeePriceEntity updatePrice(Long id, FeePriceUpdateRequest request) {
        FeePriceEntity existing = requirePrice(id);
        assertCampusWritable(existing.getCampusId());
        if (request.monthlyPrice() != null) {
            existing.setMonthlyPrice(scale(request.monthlyPrice()));
        }
        if (request.dailyPrice() != null) {
            existing.setDailyPrice(scale(request.dailyPrice()));
        }
        assertAtLeastOnePrice(existing.getMonthlyPrice(), existing.getDailyPrice());
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(requireStatus(request.status()));
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        feePriceMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deletePrice(Long id) {
        FeePriceEntity existing = requirePrice(id);
        assertCampusWritable(existing.getCampusId());
        feePriceMapper.physicalDeleteById(id);
    }

    private FeePriceEntity requirePrice(Long id) {
        FeePriceEntity entity = feePriceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收费价目不存在：" + id);
        }
        InstitutionScopeGuard.assertOwn(entity.getInstitutionId(), "收费价目");
        return entity;
    }

    private void requireItem(Long feeItemId, Long institutionId) {
        FeeItemEntity item = feeItemMapper.selectById(feeItemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收费项目不存在：" + feeItemId);
        }
        if (!Objects.equals(item.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "收费项目不属于该机构：" + item.getItemName());
        }
    }

    private Long requireInstitution(Long requested) {
        Long institutionId = InstitutionScopeGuard.resolveForQuery(requested, "收费价目");
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构不能为空");
        }
        return institutionId;
    }

    private void assertCampusWritable(long campusId) {
        if (campusId == 0L) {
            // 0 = 全校区通用价目，受限角色不能改（会影响别的校区）
            CampusScopeGuard.assertUnrestricted("维护全校区通用价目");
        } else {
            CampusScopeGuard.assertCampus(campusId, "收费价目");
        }
    }

    private void assertAtLeastOnePrice(BigDecimal monthlyPrice, BigDecimal dailyPrice) {
        if (monthlyPrice == null && dailyPrice == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "月标准与日标准至少要填一个");
        }
    }

    private void assertUniqueScope(Long institutionId, Long feeItemId, long campusId, long classId,
                                   long termId, long studentId, Long excludeId) {
        LambdaQueryWrapper<FeePriceEntity> wrapper = new LambdaQueryWrapper<FeePriceEntity>()
            .eq(FeePriceEntity::getInstitutionId, institutionId)
            .eq(FeePriceEntity::getFeeItemId, feeItemId)
            .eq(FeePriceEntity::getCampusId, campusId)
            .eq(FeePriceEntity::getClassId, classId)
            .eq(FeePriceEntity::getTermId, termId)
            .eq(FeePriceEntity::getStudentId, studentId);
        if (excludeId != null) {
            wrapper.ne(FeePriceEntity::getId, excludeId);
        }
        Long count = feePriceMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "同一组价目维度已存在（校区/班级/学期/学员不能重复）");
        }
    }

    private String requireStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) {
            return "active";
        }
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态只能是 active 或 disabled");
        }
        return normalized;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
