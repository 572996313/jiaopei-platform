package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.CourseCategoryEntity;
import com.jiaopei.platform.education.dto.CourseCategoryCreateRequest;
import com.jiaopei.platform.education.mapper.CourseCategoryMapper;
import com.jiaopei.platform.education.service.CourseCategoryService;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {

    private final CourseCategoryMapper courseCategoryMapper;
    private final InstitutionMapper institutionMapper;

    public CourseCategoryServiceImpl(CourseCategoryMapper courseCategoryMapper, InstitutionMapper institutionMapper) {
        this.courseCategoryMapper = courseCategoryMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<CourseCategoryEntity> listCategories(Long institutionId) {
        LambdaQueryWrapper<CourseCategoryEntity> wrapper = new LambdaQueryWrapper<CourseCategoryEntity>()
            .orderByAsc(CourseCategoryEntity::getInstitutionId)
            .orderByAsc(CourseCategoryEntity::getSortOrder)
            .orderByAsc(CourseCategoryEntity::getCategoryCode);
        if (institutionId != null) {
            wrapper.eq(CourseCategoryEntity::getInstitutionId, institutionId);
        }
        return courseCategoryMapper.selectList(wrapper);
    }

    @Override
    public CourseCategoryEntity createCategory(CourseCategoryCreateRequest request) {
        requireInstitution(request.institutionId());

        String categoryCode = normalize(request.categoryCode());
        String categoryName = normalize(request.categoryName());
        assertUniqueCategoryCode(request.institutionId(), categoryCode);
        assertUniqueCategoryName(request.institutionId(), categoryName);

        CourseCategoryEntity entity = new CourseCategoryEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCategoryCode(categoryCode);
        entity.setCategoryName(categoryName);
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            courseCategoryMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course category code or name already exists in institution: " + request.institutionId());
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
    }

    private void assertUniqueCategoryCode(Long institutionId, String categoryCode) {
        Long count = courseCategoryMapper.selectCount(new LambdaQueryWrapper<CourseCategoryEntity>()
            .eq(CourseCategoryEntity::getInstitutionId, institutionId)
            .eq(CourseCategoryEntity::getCategoryCode, categoryCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course category code already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueCategoryName(Long institutionId, String categoryName) {
        Long count = courseCategoryMapper.selectCount(new LambdaQueryWrapper<CourseCategoryEntity>()
            .eq(CourseCategoryEntity::getInstitutionId, institutionId)
            .eq(CourseCategoryEntity::getCategoryName, categoryName));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course category name already exists in institution: " + institutionId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
