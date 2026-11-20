package com.cornerstone.module.education.fee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePriceEntity;
import com.cornerstone.module.education.fee.dto.FeeItemCreateRequest;
import com.cornerstone.module.education.fee.dto.FeeItemUpdateRequest;
import com.cornerstone.module.education.fee.mapper.FeeItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeePriceMapper;
import com.cornerstone.module.education.fee.service.FeeItemService;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class FeeItemServiceImpl implements FeeItemService {

    private static final Set<String> BILLING_MODES = Set.of("monthly", "daily");
    private static final Set<String> STATUSES = Set.of("active", "disabled");

    private final FeeItemMapper feeItemMapper;
    private final FeePriceMapper feePriceMapper;
    private final FeeLedgerItemMapper feeLedgerItemMapper;
    private final InstitutionMapper institutionMapper;
    private final CodeRuleService codeRuleService;

    public FeeItemServiceImpl(FeeItemMapper feeItemMapper, FeePriceMapper feePriceMapper,
                              FeeLedgerItemMapper feeLedgerItemMapper,
                              InstitutionMapper institutionMapper, CodeRuleService codeRuleService) {
        this.feeItemMapper = feeItemMapper;
        this.feePriceMapper = feePriceMapper;
        this.feeLedgerItemMapper = feeLedgerItemMapper;
        this.institutionMapper = institutionMapper;
        this.codeRuleService = codeRuleService;
    }

    @Override
    public List<FeeItemEntity> listItems(Long institutionId, String status) {
        Long resolved = InstitutionScopeGuard.resolveForQuery(institutionId, "收费项目");
        LambdaQueryWrapper<FeeItemEntity> wrapper = new LambdaQueryWrapper<>();
        if (resolved != null) {
            wrapper.eq(FeeItemEntity::getInstitutionId, resolved);
        }
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null && !normalizedStatus.isBlank()) {
            wrapper.eq(FeeItemEntity::getStatus, normalizedStatus);
        }
        wrapper.orderByAsc(FeeItemEntity::getSortNo).orderByAsc(FeeItemEntity::getId);
        return feeItemMapper.selectList(wrapper);
    }

    @Override
    public FeeItemEntity createItem(FeeItemCreateRequest request) {
        Long institutionId = requireInstitution(request.institutionId());
        String itemCode = codeRuleService.resolveForCreate("fee_item", request.itemCode());
        String itemName = requireName(request.itemName());
        assertUniqueCode(institutionId, itemCode, null);

        FeeItemEntity entity = new FeeItemEntity();
        entity.setInstitutionId(institutionId);
        entity.setItemCode(itemCode);
        entity.setItemName(itemName);
        entity.setBillingMode(requireBillingMode(request.billingMode()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(requireStatus(request.status()));
        entity.setRemark(normalizeToNull(request.remark()));
        entity.setDeleted(Boolean.FALSE);
        try {
            feeItemMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下收费项目编码已存在：" + itemCode);
        }
        return entity;
    }

    @Override
    public FeeItemEntity updateItem(Long id, FeeItemUpdateRequest request) {
        FeeItemEntity existing = requireItem(id);
        if (request.itemCode() != null && !request.itemCode().isBlank()) {
            String itemCode = normalize(request.itemCode());
            if (!Objects.equals(existing.getItemCode(), itemCode)) {
                assertUniqueCode(existing.getInstitutionId(), itemCode, id);
                existing.setItemCode(itemCode);
            }
        }
        if (request.itemName() != null) {
            existing.setItemName(requireName(request.itemName()));
        }
        if (request.billingMode() != null && !request.billingMode().isBlank()) {
            existing.setBillingMode(requireBillingMode(request.billingMode()));
        }
        if (request.sortNo() != null) {
            existing.setSortNo(request.sortNo());
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(requireStatus(request.status()));
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            feeItemMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下收费项目编码已存在：" + existing.getItemCode());
        }
        return existing;
    }

    @Override
    public void deleteItem(Long id) {
        FeeItemEntity existing = requireItem(id);
        Long priceCount = feePriceMapper.selectCount(new LambdaQueryWrapper<FeePriceEntity>()
            .eq(FeePriceEntity::getFeeItemId, id));
        if (priceCount != null && priceCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "「" + existing.getItemName() + "」已配置价目，请先删除价目再删项目");
        }
        Long ledgerItemCount = feeLedgerItemMapper.selectCount(new LambdaQueryWrapper<FeeLedgerItemEntity>()
            .eq(FeeLedgerItemEntity::getFeeItemId, id));
        if (ledgerItemCount != null && ledgerItemCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "「" + existing.getItemName() + "」已被收费台账使用，不能删除；不再收费请改为「停用」");
        }
        feeItemMapper.physicalDeleteById(id);
    }

    private FeeItemEntity requireItem(Long id) {
        FeeItemEntity entity = feeItemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收费项目不存在：" + id);
        }
        return entity;
    }

    private Long requireInstitution(Long requested) {
        Long institutionId = InstitutionScopeGuard.resolveForQuery(requested, "收费项目");
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构不能为空");
        }
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
        return institutionId;
    }

    private String requireName(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "收费项目名称不能为空");
        }
        return normalized;
    }

    private String requireBillingMode(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) {
            return "monthly";
        }
        if (!BILLING_MODES.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "计费方式只能是 monthly 或 daily");
        }
        return normalized;
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

    private void assertUniqueCode(Long institutionId, String itemCode, Long excludeId) {
        LambdaQueryWrapper<FeeItemEntity> wrapper = new LambdaQueryWrapper<FeeItemEntity>()
            .eq(FeeItemEntity::getInstitutionId, institutionId)
            .eq(FeeItemEntity::getItemCode, itemCode);
        if (excludeId != null) {
            wrapper.ne(FeeItemEntity::getId, excludeId);
        }
        Long count = feeItemMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下收费项目编码已存在：" + itemCode);
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
