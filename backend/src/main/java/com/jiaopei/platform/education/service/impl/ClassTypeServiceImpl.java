package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ClassTypeEntity;
import com.jiaopei.platform.education.dto.ClassTypeCreateRequest;
import com.jiaopei.platform.education.dto.ClassTypeUpdateRequest;
import com.jiaopei.platform.education.mapper.ClassTypeMapper;
import com.jiaopei.platform.education.service.ClassTypeService;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.util.List;
import java.util.Objects;
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "maxStudents 不能小于 minStudents");
        }

        String typeCode = normalize(request.typeCode());
        String typeName = normalize(request.typeName());
        assertUniqueTypeCode(request.institutionId(), typeCode, null);
        assertUniqueTypeName(request.institutionId(), typeName, null);

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
            throw new BusinessException(ResultCode.BAD_REQUEST, "maxStudents 不能小于 minStudents");
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
            existing.setTeachingMethod(normalizeToNull(request.teachingMethod()));
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
