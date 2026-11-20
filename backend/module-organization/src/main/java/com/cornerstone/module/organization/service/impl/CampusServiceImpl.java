package com.cornerstone.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleDataScopeEntity;
import com.cornerstone.core.auth.mapper.RoleDataScopeMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.module.organization.dto.CampusCreateRequest;
import com.cornerstone.module.organization.dto.CampusUpdateRequest;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.module.organization.service.CampusService;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.cornerstone.module.organization.service.support.DictValueValidator;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CampusServiceImpl implements CampusService {

    private final CampusMapper campusMapper;
    private final InstitutionMapper institutionMapper;
    private final DictValueValidator dictValueValidator;
    private final CodeRuleService codeRuleService;
    private final RoleDataScopeMapper roleDataScopeMapper;

    /** 四参构造保留给纯单测：没有角色范围表时跳过删除前的引用检查。 */
    public CampusServiceImpl(CampusMapper campusMapper, InstitutionMapper institutionMapper,
                             DictValueValidator dictValueValidator, CodeRuleService codeRuleService) {
        this(campusMapper, institutionMapper, dictValueValidator, codeRuleService, null);
    }

    @Autowired
    public CampusServiceImpl(CampusMapper campusMapper, InstitutionMapper institutionMapper,
                             DictValueValidator dictValueValidator, CodeRuleService codeRuleService,
                             RoleDataScopeMapper roleDataScopeMapper) {
        this.campusMapper = campusMapper;
        this.institutionMapper = institutionMapper;
        this.dictValueValidator = dictValueValidator;
        this.codeRuleService = codeRuleService;
        this.roleDataScopeMapper = roleDataScopeMapper;
    }

    @Override
    public List<CampusEntity> listCampuses(Long institutionId) {
        Long resolvedInstitutionId = InstitutionScopeGuard.resolveForQuery(institutionId, "校区");
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
            .orderByAsc(CampusEntity::getSortNo)
            .orderByAsc(CampusEntity::getCampusCode);
        if (resolvedInstitutionId != null) {
            wrapper.eq(CampusEntity::getInstitutionId, resolvedInstitutionId);
        }
        // 受限角色只能看到被授权的校区；空白名单时这里是 1=0，不能放开成全部
        CampusScopeGuard.apply(wrapper, CampusEntity::getId);
        return campusMapper.selectList(wrapper);
    }

    @Override
    public CampusEntity createCampus(CampusCreateRequest request) {
        CampusScopeGuard.assertUnrestricted("新建校区");
        InstitutionScopeGuard.assertOwn(request.institutionId(), "校区");
        InstitutionEntity institution = institutionMapper.selectById(request.institutionId());
        if (institution == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + request.institutionId());
        }
        String campusCode = codeRuleService.resolveForCreate("campus", request.campusCode());
        String campusName = normalize(request.campusName());
        assertUniqueCampusCode(request.institutionId(), campusCode, null);
        assertUniqueCampusName(request.institutionId(), campusName, null);

        CampusEntity entity = new CampusEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusCode(campusCode);
        entity.setCampusName(campusName);
        String campusType = dictValueValidator.validate("campus_type", "校区类型", request.campusType());
        entity.setCampusType(campusType == null ? "direct" : campusType);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区编码或校区名称已存在：" + campusCode);
        }
        return entity;
    }

    @Override
    public CampusEntity updateCampus(Long id, CampusUpdateRequest request) {
        CampusEntity existing = requireCampus(id);
        InstitutionScopeGuard.assertOwn(existing.getInstitutionId(), "校区");
        CampusScopeGuard.assertCampus(existing.getId(), "校区");
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
        String campusType = dictValueValidator.validate("campus_type", "校区类型", request.campusType());
        if (campusType != null) {
            existing.setCampusType(campusType);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区编码或校区名称已存在：" + existing.getCampusCode());
        }
        return existing;
    }

    @Override
    public void deleteCampus(Long id) {
        CampusEntity existing = requireCampus(id);
        InstitutionScopeGuard.assertOwn(existing.getInstitutionId(), "校区");
        CampusScopeGuard.assertCampus(existing.getId(), "校区");
        // 被角色授权引用的校区不能删：删掉后授权行只剩一个查不到的 ID，角色会静默变成"少一个校区"
        if (roleDataScopeMapper != null) {
            Long refs = roleDataScopeMapper.selectCount(new LambdaQueryWrapper<RoleDataScopeEntity>()
                .eq(RoleDataScopeEntity::getInstitutionId, existing.getInstitutionId())
                .eq(RoleDataScopeEntity::getScopeType, CampusScopeGuard.DIMENSION_KEY)
                .eq(RoleDataScopeEntity::getScopeValue, id));
            if (refs != null && refs > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该校区仍被 " + refs + " 个角色授权引用，请先在角色授权里移除后再删除");
            }
        }
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区编码已存在：" + campusCode);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下校区名称已存在：" + campusName);
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
