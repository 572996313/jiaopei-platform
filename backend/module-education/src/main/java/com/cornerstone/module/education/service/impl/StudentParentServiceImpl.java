package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.StudentParentEntity;
import com.cornerstone.module.education.dto.StudentParentCreateRequest;
import com.cornerstone.module.education.dto.StudentParentPageQuery;
import com.cornerstone.module.education.dto.StudentParentView;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.StudentParentMapper;
import com.cornerstone.module.education.service.StudentParentService;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class StudentParentServiceImpl implements StudentParentService {

    private final StudentParentMapper studentParentMapper;
    private final StudentMapper studentMapper;
    private final InstitutionMapper institutionMapper;

    public StudentParentServiceImpl(StudentParentMapper studentParentMapper, StudentMapper studentMapper,
                                    InstitutionMapper institutionMapper) {
        this.studentParentMapper = studentParentMapper;
        this.studentMapper = studentMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<StudentParentEntity> listStudentParents(Long institutionId, Long studentId) {
        LambdaQueryWrapper<StudentParentEntity> wrapper = new LambdaQueryWrapper<StudentParentEntity>()
            .orderByAsc(StudentParentEntity::getStudentId)
            .orderByDesc(StudentParentEntity::getIsPrimary)
            .orderByAsc(StudentParentEntity::getId);
        if (institutionId != null) {
            wrapper.eq(StudentParentEntity::getInstitutionId, institutionId);
        }
        if (studentId != null) {
            wrapper.eq(StudentParentEntity::getStudentId, studentId);
        }
        return studentParentMapper.selectList(wrapper);
    }

    @Override
    public PageResult<StudentParentView> pageStudentParents(StudentParentPageQuery query) {
        LambdaQueryWrapper<StudentParentEntity> wrapper = new LambdaQueryWrapper<StudentParentEntity>()
            .orderByAsc(StudentParentEntity::getStudentId)
            .orderByDesc(StudentParentEntity::getIsPrimary)
            .orderByAsc(StudentParentEntity::getId);
        if (query.getInstitutionId() != null) {
            wrapper.eq(StudentParentEntity::getInstitutionId, query.getInstitutionId());
        }
        if (query.getStudentId() != null) {
            wrapper.eq(StudentParentEntity::getStudentId, query.getStudentId());
        }
        Page<StudentParentEntity> page = studentParentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords()));
    }

    /** 批量回带学员名称,前端分页列表无需再全量加载学员做名称回显 */
    private List<StudentParentView> toViews(List<StudentParentEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(entities.stream()
                .map(StudentParentEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        return entities.stream()
            .map(entity -> {
                StudentEntity student = studentMap.get(entity.getStudentId());
                return new StudentParentView(
                    entity.getId(),
                    entity.getInstitutionId(),
                    entity.getStudentId(),
                    student == null ? null : student.getStudentNo(),
                    student == null ? null : student.getStudentName(),
                    entity.getParentName(),
                    entity.getRelationType(),
                    entity.getParentPhone(),
                    entity.getWechatNo(),
                    entity.getIsPrimary(),
                    entity.getIsEmergencyContact(),
                    entity.getStatus(),
                    entity.getRemark()
                );
            })
            .toList();
    }

    @Override
    public StudentParentEntity createStudentParent(StudentParentCreateRequest request) {
        return saveStudentParent(null, request);
    }

    @Override
    public StudentParentEntity updateStudentParent(Long id, StudentParentCreateRequest request) {
        requireStudentParent(id);
        return saveStudentParent(id, request);
    }

    @Override
    public void deleteStudentParent(Long id) {
        StudentParentEntity entity = requireStudentParent(id);
        StudentEntity student = studentMapper.selectById(entity.getStudentId());
        studentParentMapper.deleteById(id);
        if (student != null) {
            StudentParentEntity currentPrimary = syncPrimaryParent(student.getId());
            syncStudentGuardian(student, currentPrimary);
        }
    }

    private StudentParentEntity saveStudentParent(Long id, StudentParentCreateRequest request) {
        requireInstitution(request.institutionId());
        StudentEntity student = requireStudentInInstitution(request.studentId(), request.institutionId());

        String parentPhone = normalizeToNull(request.parentPhone());
        if (parentPhone != null) {
            assertUniqueParentPhone(request.studentId(), parentPhone, id);
        }
        if (Boolean.TRUE.equals(request.isPrimary())) {
            demoteOtherPrimaryParents(request.studentId(), id);
        }

        StudentParentEntity entity = id == null ? new StudentParentEntity() : requireStudentParent(id);
        entity.setId(id);
        entity.setInstitutionId(request.institutionId());
        entity.setStudentId(request.studentId());
        entity.setParentName(request.parentName().trim());
        entity.setRelationType(normalizeToNull(request.relationType()));
        entity.setParentPhone(parentPhone);
        entity.setWechatNo(normalizeToNull(request.wechatNo()));
        entity.setIsPrimary(Boolean.TRUE.equals(request.isPrimary()));
        entity.setIsEmergencyContact(Boolean.TRUE.equals(request.isEmergencyContact()));
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setRemark(request.remark());
        if (id == null) {
            entity.setDeleted(Boolean.FALSE);
            try {
                studentParentMapper.insert(entity);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该学员下家长联系电话已存在：" + request.studentId());
            }
        } else {
            studentParentMapper.updateById(entity);
        }
        StudentParentEntity currentPrimary = syncPrimaryParent(student.getId());
        syncStudentGuardian(student, currentPrimary);
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private StudentEntity requireStudentInInstitution(Long studentId, Long institutionId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + studentId);
        }
        if (!Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员不属于该机构：" + institutionId);
        }
        return student;
    }

    private void assertUniqueParentPhone(Long studentId, String parentPhone, Long excludeId) {
        LambdaQueryWrapper<StudentParentEntity> wrapper = new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getParentPhone, parentPhone);
        if (excludeId != null) {
            wrapper.ne(StudentParentEntity::getId, excludeId);
        }
        Long count = studentParentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该学员下家长联系电话已存在：" + studentId);
        }
    }

    private void demoteOtherPrimaryParents(Long studentId, Long excludeId) {
        LambdaQueryWrapper<StudentParentEntity> wrapper = new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getIsPrimary, Boolean.TRUE);
        if (excludeId != null) {
            wrapper.ne(StudentParentEntity::getId, excludeId);
        }
        List<StudentParentEntity> existingPrimaryParents = studentParentMapper.selectList(wrapper);
        for (StudentParentEntity existingPrimaryParent : existingPrimaryParents) {
            existingPrimaryParent.setIsPrimary(Boolean.FALSE);
            studentParentMapper.updateById(existingPrimaryParent);
        }
    }

    private StudentParentEntity syncPrimaryParent(Long studentId) {
        StudentParentEntity primaryParent = studentParentMapper.selectOne(new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getIsPrimary, Boolean.TRUE)
            .last("LIMIT 1"));
        if (primaryParent != null) {
            return primaryParent;
        }

        StudentParentEntity fallbackParent = studentParentMapper.selectOne(new LambdaQueryWrapper<StudentParentEntity>()
            .eq(StudentParentEntity::getStudentId, studentId)
            .eq(StudentParentEntity::getStatus, "active")
            .orderByAsc(StudentParentEntity::getId)
            .last("LIMIT 1"));
        if (fallbackParent != null) {
            fallbackParent.setIsPrimary(Boolean.TRUE);
            studentParentMapper.updateById(fallbackParent);
        }
        return fallbackParent;
    }

    private void syncStudentGuardian(StudentEntity student, StudentParentEntity currentPrimary) {
        String guardianName = currentPrimary == null ? null : currentPrimary.getParentName();
        String guardianPhone = currentPrimary == null ? null : currentPrimary.getParentPhone();
        if (!Objects.equals(student.getGuardianName(), guardianName)
            || !Objects.equals(student.getGuardianPhone(), guardianPhone)) {
            student.setGuardianName(guardianName);
            student.setGuardianPhone(guardianPhone);
            studentMapper.updateById(student);
        }
    }

    private StudentParentEntity requireStudentParent(Long id) {
        StudentParentEntity entity = studentParentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "家长联系人不存在：" + id);
        }
        return entity;
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
