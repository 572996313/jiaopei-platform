package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.tenant.domain.InstitutionEntity;
import com.jiaopei.platform.organization.dto.CampusCreateRequest;
import com.jiaopei.platform.organization.dto.CampusUpdateRequest;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.tenant.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.service.CampusService;
import java.util.List;
import java.util.Objects;
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
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + request.institutionId());
        }
        String campusCode = normalize(request.campusCode());
        String campusName = normalize(request.campusName());
        assertUniqueCampusCode(request.institutionId(), campusCode, null);
        assertUniqueCampusName(request.institutionId(), campusName, null);

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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区编码或校区名称已存在：" + request.institutionId());
        }
        return entity;
    }

    @Override
    public CampusEntity updateCampus(Long id, CampusUpdateRequest request) {
        CampusEntity existing = requireCampus(id);
        if (request.campusCode() != null && !request.campusCode().isBlank()) {
            String campusCode = normalize(request.campusCode());
            if (!Objects.equals(existing.getCampusCode(), campusCode)) {
                assertUniqueCampusCode(existing.getInstitutionId(), campusCode, id);
                existing.setCampusCode(campusCode);
            }
        }
        if (request.campusName() != null && !request.campusName().isBlank()) {
            String campusName = normalize(request.campusName());
            if (!Objects.equals(existing.getCampusName(), campusName)) {
                assertUniqueCampusName(existing.getInstitutionId(), campusName, id);
                existing.setCampusName(campusName);
            }
        }
        if (request.campusType() != null && !request.campusType().isBlank()) {
            existing.setCampusType(request.campusType().trim());
        }
        if (request.phone() != null) {
            existing.setPhone(normalizeToNull(request.phone()));
        }
        if (request.principalName() != null) {
            existing.setPrincipalName(normalizeToNull(request.principalName()));
        }
        if (request.provinceCode() != null) {
            existing.setProvinceCode(normalizeToNull(request.provinceCode()));
        }
        if (request.cityCode() != null) {
            existing.setCityCode(normalizeToNull(request.cityCode()));
        }
        if (request.districtCode() != null) {
            existing.setDistrictCode(normalizeToNull(request.districtCode()));
        }
        if (request.address() != null) {
            existing.setAddress(normalizeToNull(request.address()));
        }
        if (request.capacityLimit() != null) {
            existing.setCapacityLimit(request.capacityLimit());
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.openedAt() != null) {
            existing.setOpenedAt(request.openedAt());
        }
        if (request.sortNo() != null) {
            existing.setSortNo(request.sortNo());
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            campusMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区编码或校区名称已存在：" + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteCampus(Long id) {
        requireCampus(id);
        campusMapper.deleteById(id);
    }

    private CampusEntity requireCampus(Long id) {
        CampusEntity entity = campusMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + id);
        }
        return entity;
    }

    private void assertUniqueCampusCode(Long institutionId, String campusCode, Long excludeId) {
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId)
            .eq(CampusEntity::getCampusCode, campusCode);
        if (excludeId != null) {
            wrapper.ne(CampusEntity::getId, excludeId);
        }
        Long count = campusMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区编码已存在：" + institutionId);
        }
    }

    private void assertUniqueCampusName(Long institutionId, String campusName, Long excludeId) {
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId)
            .eq(CampusEntity::getCampusName, campusName);
        if (excludeId != null) {
            wrapper.ne(CampusEntity::getId, excludeId);
        }
        Long count = campusMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区名称已存在：" + institutionId);
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
