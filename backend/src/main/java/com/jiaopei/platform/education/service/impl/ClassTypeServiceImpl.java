package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ClassTypeEntity;
import com.jiaopei.platform.education.dto.ClassTypeCreateRequest;
import com.jiaopei.platform.education.mapper.ClassTypeMapper;
import com.jiaopei.platform.education.service.ClassTypeService;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ClassTypeServiceImpl implements ClassTypeService {

    private final ClassTypeMapper classTypeMapper;
    private final InstitutionMapper institutionMapper;

    public ClassTypeServiceImpl(ClassTypeMapper classTypeMapper, InstitutionMapper institutionMapper) {
        this.classTypeMapper = classTypeMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<ClassTypeEntity> listClassTypes(Long institutionId) {
        LambdaQueryWrapper<ClassTypeEntity> wrapper = new LambdaQueryWrapper<ClassTypeEntity>()
            .orderByAsc(ClassTypeEntity::getInstitutionId)
            .orderByAsc(ClassTypeEntity::getTypeCode);
        if (institutionId != null) {
            wrapper.eq(ClassTypeEntity::getInstitutionId, institutionId);
        }
        return classTypeMapper.selectList(wrapper);
    }

    @Override
    public ClassTypeEntity createClassType(ClassTypeCreateRequest request) {
        requireInstitution(request.institutionId());
        int minStudents = request.minStudents() == null ? 0 : request.minStudents();
        Integer maxStudents = request.maxStudents();
        if (maxStudents != null && maxStudents < minStudents) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "maxStudents cannot be less than minStudents");
        }

        String typeCode = normalize(request.typeCode());
        String typeName = normalize(request.typeName());
        assertUniqueTypeCode(request.institutionId(), typeCode);
        assertUniqueTypeName(request.institutionId(), typeName);

        ClassTypeEntity entity = new ClassTypeEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setTypeCode(typeCode);
        entity.setTypeName(typeName);
        entity.setTeachingMethod(normalizeToNull(request.teachingMethod()));
        entity.setMinStudents(minStudents);
        entity.setMaxStudents(maxStudents);
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            classTypeMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Class type code or name already exists in institution: " + request.institutionId());
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
    }

    private void assertUniqueTypeCode(Long institutionId, String typeCode) {
        Long count = classTypeMapper.selectCount(new LambdaQueryWrapper<ClassTypeEntity>()
            .eq(ClassTypeEntity::getInstitutionId, institutionId)
            .eq(ClassTypeEntity::getTypeCode, typeCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Class type code already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueTypeName(Long institutionId, String typeName) {
        Long count = classTypeMapper.selectCount(new LambdaQueryWrapper<ClassTypeEntity>()
            .eq(ClassTypeEntity::getInstitutionId, institutionId)
            .eq(ClassTypeEntity::getTypeName, typeName));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Class type name already exists in institution: " + institutionId);
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
