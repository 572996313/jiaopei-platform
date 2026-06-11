package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.dto.InstitutionCreateRequest;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.service.InstitutionService;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

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

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
