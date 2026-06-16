package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.dto.CurrentInstitutionUpdateRequest;
import com.jiaopei.platform.organization.dto.CurrentInstitutionView;
import com.jiaopei.platform.organization.dto.InstitutionCreateRequest;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.service.InstitutionService;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstitutionServiceImpl implements InstitutionService {

    private final InstitutionMapper institutionMapper;

    public InstitutionServiceImpl(InstitutionMapper institutionMapper) {
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<InstitutionEntity> listInstitutions() {
        return institutionMapper.selectList(new LambdaQueryWrapper<InstitutionEntity>()
            .orderByAsc(InstitutionEntity::getOrgCode));
    }

    @Override
    public InstitutionEntity createInstitution(InstitutionCreateRequest request) {
        String orgCode = normalize(request.orgCode());
        String orgName = normalize(request.orgName());
        assertUniqueOrgCode(orgCode);
        assertUniqueOrgName(orgName);

        InstitutionEntity entity = new InstitutionEntity();
        entity.setOrgCode(orgCode);
        entity.setOrgName(orgName);
        entity.setShortName(request.shortName());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setProvinceCode(request.provinceCode());
        entity.setCityCode(request.cityCode());
        entity.setDistrictCode(request.districtCode());
        entity.setAddress(request.address());
        entity.setTimezone(request.timezone() == null || request.timezone().isBlank() ? "Asia/Shanghai" : request.timezone());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "draft" : request.status());
        entity.setOpenedAt(request.openedAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            institutionMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Institution orgCode or orgName already exists");
        }
        return entity;
    }

    @Override
    public CurrentInstitutionView getCurrentInstitution(Long institutionId) {
        return toCurrentView(requireInstitution(institutionId));
    }

    @Override
    @Transactional
    public CurrentInstitutionView updateCurrentInstitution(Long institutionId, CurrentInstitutionUpdateRequest request) {
        InstitutionEntity entity = requireInstitution(institutionId);
        if (request.name() != null) {
            String orgName = requireNonBlank(request.name(), "name");
            if (!Objects.equals(entity.getOrgName(), orgName)) {
                assertUniqueOrgName(orgName, institutionId);
            }
            entity.setOrgName(orgName);
        }
        if (request.shortName() != null) {
            entity.setShortName(normalizeToNull(request.shortName()));
        }
        if (request.contactName() != null) {
            entity.setContactName(normalizeToNull(request.contactName()));
        }
        if (request.contactPhone() != null) {
            entity.setContactPhone(normalizeToNull(request.contactPhone()));
        }
        if (request.address() != null) {
            entity.setAddress(normalizeToNull(request.address()));
        }
        if (request.status() != null) {
            entity.setStatus(requireNonBlank(request.status(), "status"));
        }
        if (request.remark() != null) {
            entity.setRemark(normalizeToNull(request.remark()));
        }
        try {
            institutionMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Institution name already exists");
        }
        return toCurrentView(requireInstitution(institutionId));
    }

    private void assertUniqueOrgCode(String orgCode) {
        Long count = institutionMapper.selectCount(new LambdaQueryWrapper<InstitutionEntity>()
            .eq(InstitutionEntity::getOrgCode, orgCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Institution orgCode already exists: " + orgCode);
        }
    }

    private void assertUniqueOrgName(String orgName) {
        Long count = institutionMapper.selectCount(new LambdaQueryWrapper<InstitutionEntity>()
            .eq(InstitutionEntity::getOrgName, orgName));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Institution orgName already exists: " + orgName);
        }
    }

    private void assertUniqueOrgName(String orgName, Long excludeInstitutionId) {
        Long count = institutionMapper.selectCount(new LambdaQueryWrapper<InstitutionEntity>()
            .eq(InstitutionEntity::getOrgName, orgName)
            .ne(InstitutionEntity::getId, excludeInstitutionId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Institution name already exists: " + orgName);
        }
    }

    private InstitutionEntity requireInstitution(Long institutionId) {
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Current user institution is missing");
        }
        InstitutionEntity institution = institutionMapper.selectById(institutionId);
        if (institution == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
        return institution;
    }

    private CurrentInstitutionView toCurrentView(InstitutionEntity entity) {
        return new CurrentInstitutionView(
            entity.getId(),
            entity.getOrgCode(),
            entity.getOrgName(),
            entity.getShortName(),
            entity.getContactName(),
            entity.getContactPhone(),
            entity.getAddress(),
            entity.getStatus(),
            entity.getRemark()
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String requireNonBlank(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + " must not be blank");
        }
        return normalized;
    }
}
