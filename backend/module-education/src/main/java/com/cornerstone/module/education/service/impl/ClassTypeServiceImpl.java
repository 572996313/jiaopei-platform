package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.dto.ClassTypeCreateRequest;
import com.cornerstone.module.education.dto.ClassTypeUpdateRequest;
import com.cornerstone.module.education.mapper.ClassTypeMapper;
import com.cornerstone.module.education.service.ClassTypeService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ClassTypeServiceImpl implements ClassTypeService {

    private final ClassTypeMapper classTypeMapper;
    private final InstitutionMapper institutionMapper;
    private final DictValueValidator dictValueValidator;

    public ClassTypeServiceImpl(ClassTypeMapper classTypeMapper, InstitutionMapper institutionMapper,
                                DictValueValidator dictValueValidator) {
        this.classTypeMapper = classTypeMapper;
        this.institutionMapper = institutionMapper;
        this.dictValueValidator = dictValueValidator;
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "最大学员数不能小于最小学员数");
        }

        String typeCode = normalize(request.typeCode());
        String typeName = normalize(request.typeName());
        assertUniqueTypeCode(request.institutionId(), typeCode, null);
        assertUniqueTypeName(request.institutionId(), typeName, null);

        ClassTypeEntity entity = new ClassTypeEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setTypeCode(typeCode);
        entity.setTypeName(typeName);
        entity.setTeachingMethod(dictValueValidator.validate("teaching_method", "授课方式", request.teachingMethod()));
        entity.setMinStudents(minStudents);
        entity.setMaxStudents(maxStudents);
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            classTypeMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下班型编码或名称已存在：" + request.institutionId());
        }
        return entity;
    }

    @Override
    public ClassTypeEntity updateClassType(Long id, ClassTypeUpdateRequest request) {
        ClassTypeEntity existing = requireClassType(id);
        int minStudents = request.minStudents() == null ? existing.getMinStudents() : request.minStudents();
        Integer maxStudents = request.maxStudents() == null ? existing.getMaxStudents() : request.maxStudents();
        if (maxStudents != null && maxStudents < minStudents) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "最大学员数不能小于最小学员数");
        }
        if (request.typeCode() != null && !request.typeCode().isBlank()) {
            String typeCode = normalize(request.typeCode());
            if (!Objects.equals(existing.getTypeCode(), typeCode)) {
                assertUniqueTypeCode(existing.getInstitutionId(), typeCode, id);
                existing.setTypeCode(typeCode);
            }
        }
        if (request.typeName() != null && !request.typeName().isBlank()) {
            String typeName = normalize(request.typeName());
            if (!Objects.equals(existing.getTypeName(), typeName)) {
                assertUniqueTypeName(existing.getInstitutionId(), typeName, id);
                existing.setTypeName(typeName);
            }
        }
        if (request.teachingMethod() != null) {
            existing.setTeachingMethod(dictValueValidator.validate("teaching_method", "授课方式", request.teachingMethod()));
        }
        if (request.minStudents() != null) {
            existing.setMinStudents(request.minStudents());
        }
        if (request.maxStudents() != null) {
            existing.setMaxStudents(request.maxStudents());
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            classTypeMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下班型编码或名称已存在：" + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteClassType(Long id) {
        requireClassType(id);
        classTypeMapper.deleteById(id);
    }

    private ClassTypeEntity requireClassType(Long id) {
        ClassTypeEntity entity = classTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班型不存在：" + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private void assertUniqueTypeCode(Long institutionId, String typeCode, Long excludeId) {
        LambdaQueryWrapper<ClassTypeEntity> wrapper = new LambdaQueryWrapper<ClassTypeEntity>()
            .eq(ClassTypeEntity::getInstitutionId, institutionId)
            .eq(ClassTypeEntity::getTypeCode, typeCode);
        if (excludeId != null) {
            wrapper.ne(ClassTypeEntity::getId, excludeId);
        }
        Long count = classTypeMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下班型编码已存在：" + institutionId);
        }
    }

    private void assertUniqueTypeName(Long institutionId, String typeName, Long excludeId) {
        LambdaQueryWrapper<ClassTypeEntity> wrapper = new LambdaQueryWrapper<ClassTypeEntity>()
            .eq(ClassTypeEntity::getInstitutionId, institutionId)
            .eq(ClassTypeEntity::getTypeName, typeName);
        if (excludeId != null) {
            wrapper.ne(ClassTypeEntity::getId, excludeId);
        }
        Long count = classTypeMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下班型名称已存在：" + institutionId);
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
