package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.dto.CampusCreateRequest;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.service.CampusService;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CampusServiceImpl implements CampusService {

    private final CampusMapper campusMapper;
    private final InstitutionMapper institutionMapper;

    public CampusServiceImpl(CampusMapper campusMapper, InstitutionMapper institutionMapper) {
        this.campusMapper = campusMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<CampusEntity> listCampuses(Long institutionId) {
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
            .orderByAsc(CampusEntity::getInstitutionId)
            .orderByAsc(CampusEntity::getSortNo)
            .orderByAsc(CampusEntity::getCampusCode);
        if (institutionId != null) {
            wrapper.eq(CampusEntity::getInstitutionId, institutionId);
        }
        return campusMapper.selectList(wrapper);
    }

    @Override
    public CampusEntity createCampus(CampusCreateRequest request) {
        InstitutionEntity institution = institutionMapper.selectById(request.institutionId());
        if (institution == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + request.institutionId());
        }
        String campusCode = normalize(request.campusCode());
        String campusName = normalize(request.campusName());
        assertUniqueCampusCode(request.institutionId(), campusCode);
        assertUniqueCampusName(request.institutionId(), campusName);

        CampusEntity entity = new CampusEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusCode(campusCode);
        entity.setCampusName(campusName);
        entity.setCampusType(request.campusType() == null || request.campusType().isBlank() ? "direct" : request.campusType());
        entity.setPhone(request.phone());
        entity.setPrincipalName(request.principalName());
        entity.setProvinceCode(request.provinceCode());
        entity.setCityCode(request.cityCode());
        entity.setDistrictCode(request.districtCode());
        entity.setAddress(request.address());
        entity.setCapacityLimit(request.capacityLimit());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "planning" : request.status());
        entity.setOpenedAt(request.openedAt());
        entity.setSortNo(request.sortNo());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            campusMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Campus campusCode or campusName already exists in institution: " + request.institutionId());
        }
        return entity;
    }

    private void assertUniqueCampusCode(Long institutionId, String campusCode) {
        Long count = campusMapper.selectCount(new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId)
            .eq(CampusEntity::getCampusCode, campusCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Campus campusCode already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueCampusName(Long institutionId, String campusName) {
        Long count = campusMapper.selectCount(new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId)
            .eq(CampusEntity::getCampusName, campusName));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Campus campusName already exists in institution: " + institutionId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
