package com.cornerstone.module.organization.service.impl;

import com.cornerstone.core.auth.service.CampusBelongingValidator;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import org.springframework.stereotype.Component;

/**
 * 校区归属校验器实现：按 jp_campus 表校验校区与机构的从属关系。
 */
@Component
public class CampusBelongingValidatorImpl implements CampusBelongingValidator {

    private final CampusMapper campusMapper;

    public CampusBelongingValidatorImpl(CampusMapper campusMapper) {
        this.campusMapper = campusMapper;
    }

    @Override
    public void assertCampusBelongsToInstitution(Long campusId, Long institutionId) {
        if (campusId == null) {
            return;
        }
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null || !institutionId.equals(campus.getInstitutionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "默认校区不属于该机构");
        }
    }
}
