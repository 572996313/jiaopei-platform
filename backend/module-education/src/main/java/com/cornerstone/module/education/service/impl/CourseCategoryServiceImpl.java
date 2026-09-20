package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.CourseCategoryEntity;
import com.cornerstone.module.education.dto.CourseCategoryCreateRequest;
import com.cornerstone.module.education.dto.CourseCategoryUpdateRequest;
import com.cornerstone.module.education.mapper.CourseCategoryMapper;
import com.cornerstone.module.education.service.CourseCategoryService;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.List;
import java.util.Objects;
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
        assertUniqueCategoryCode(request.institutionId(), categoryCode, null);
        assertUniqueCategoryName(request.institutionId(), categoryName, null);

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
                "该机构下课程分类编码或名称已存在：" + request.institutionId());
        }
        return entity;
    }

    @Override
    public CourseCategoryEntity updateCategory(Long id, CourseCategoryUpdateRequest request) {
        CourseCategoryEntity existing = requireCategory(id);
        if (request.categoryCode() != null && !request.categoryCode().isBlank()) {
            String categoryCode = normalize(request.categoryCode());
            if (!Objects.equals(existing.getCategoryCode(), categoryCode)) {
                assertUniqueCategoryCode(existing.getInstitutionId(), categoryCode, id);
                existing.setCategoryCode(categoryCode);
            }
        }
        if (request.categoryName() != null && !request.categoryName().isBlank()) {
            String categoryName = normalize(request.categoryName());
            if (!Objects.equals(existing.getCategoryName(), categoryName)) {
                assertUniqueCategoryName(existing.getInstitutionId(), categoryName, id);
                existing.setCategoryName(categoryName);
            }
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.sortOrder() != null) {
            existing.setSortOrder(request.sortOrder());
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            courseCategoryMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下课程分类编码或名称已存在：" + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteCategory(Long id) {
        requireCategory(id);
        courseCategoryMapper.deleteById(id);
    }

    private CourseCategoryEntity requireCategory(Long id) {
        CourseCategoryEntity entity = courseCategoryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程分类不存在：" + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private void assertUniqueCategoryCode(Long institutionId, String categoryCode, Long excludeId) {
        LambdaQueryWrapper<CourseCategoryEntity> wrapper = new LambdaQueryWrapper<CourseCategoryEntity>()
            .eq(CourseCategoryEntity::getInstitutionId, institutionId)
            .eq(CourseCategoryEntity::getCategoryCode, categoryCode);
        if (excludeId != null) {
            wrapper.ne(CourseCategoryEntity::getId, excludeId);
        }
        Long count = courseCategoryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下课程分类编码已存在：" + institutionId);
        }
    }

    private void assertUniqueCategoryName(Long institutionId, String categoryName, Long excludeId) {
        LambdaQueryWrapper<CourseCategoryEntity> wrapper = new LambdaQueryWrapper<CourseCategoryEntity>()
            .eq(CourseCategoryEntity::getInstitutionId, institutionId)
            .eq(CourseCategoryEntity::getCategoryName, categoryName);
        if (excludeId != null) {
            wrapper.ne(CourseCategoryEntity::getId, excludeId);
        }
        Long count = courseCategoryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下课程分类名称已存在：" + institutionId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
