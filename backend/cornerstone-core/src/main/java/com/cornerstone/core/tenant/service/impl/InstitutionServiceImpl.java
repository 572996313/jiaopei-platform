package com.cornerstone.core.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.dto.CurrentInstitutionUpdateRequest;
import com.cornerstone.core.tenant.dto.CurrentInstitutionView;
import com.cornerstone.core.tenant.dto.InstitutionCreateRequest;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.tenant.service.InstitutionService;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstitutionServiceImpl implements InstitutionService {

    private final InstitutionMapper institutionMapper;
    private final CodeRuleService codeRuleService;

    public InstitutionServiceImpl(InstitutionMapper institutionMapper, CodeRuleService codeRuleService) {
        this.institutionMapper = institutionMapper;
        this.codeRuleService = codeRuleService;
    }

    @Override
    public List<InstitutionEntity> listInstitutions() {
        LambdaQueryWrapper<InstitutionEntity> wrapper = new LambdaQueryWrapper<InstitutionEntity>()
            .orderByAsc(InstitutionEntity::getOrgCode);
        // 机构列表是全站公共数据（机构芯片、各业务页的 institutionId 都取自它），
        // 所以读取不挂权限码：平台管理员看全量，其他账号只看本机构。
        AuthenticatedUser user = AuthContext.currentUser().orElse(null);
        if (user != null && !isPlatformAdmin(user)) {
            if (user.orgId() == null) {
                return List.of();
            }
            wrapper.eq(InstitutionEntity::getId, user.orgId());
        }
        return institutionMapper.selectList(wrapper);
    }

    private static boolean isPlatformAdmin(AuthenticatedUser user) {
        return user.roles() != null && user.roles().contains("PLATFORM_ADMIN");
    }

    @Override
    public InstitutionEntity createInstitution(InstitutionCreateRequest request) {
        // 留空即由编号规则补号（未启用规则时 resolveForCreate 仍报"机构编码不能为空"）
        String orgCode = codeRuleService.resolveForCreate("institution", request.orgCode());
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构编码或机构名称已存在");
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
            String orgName = requireNonBlank(request.name(), "机构名称");
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
            entity.setStatus(requireNonBlank(request.status(), "状态"));
        }
        if (request.remark() != null) {
            entity.setRemark(normalizeToNull(request.remark()));
        }
        try {
            institutionMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构名称已存在");
        }
        return toCurrentView(requireInstitution(institutionId));
    }

    private void assertUniqueOrgCode(String orgCode) {
        Long count = institutionMapper.selectCount(new LambdaQueryWrapper<InstitutionEntity>()
            .eq(InstitutionEntity::getOrgCode, orgCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构编码已存在：" + orgCode);
        }
    }

    private void assertUniqueOrgName(String orgName) {
        Long count = institutionMapper.selectCount(new LambdaQueryWrapper<InstitutionEntity>()
            .eq(InstitutionEntity::getOrgName, orgName));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构名称已存在：" + orgName);
        }
    }

    private void assertUniqueOrgName(String orgName, Long excludeInstitutionId) {
        Long count = institutionMapper.selectCount(new LambdaQueryWrapper<InstitutionEntity>()
            .eq(InstitutionEntity::getOrgName, orgName)
            .ne(InstitutionEntity::getId, excludeInstitutionId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构名称已存在：" + orgName);
        }
    }

    private InstitutionEntity requireInstitution(Long institutionId) {
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前登录用户缺少所属机构");
        }
        InstitutionEntity institution = institutionMapper.selectById(institutionId);
        if (institution == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "不能为空");
        }
        return normalized;
    }
}
