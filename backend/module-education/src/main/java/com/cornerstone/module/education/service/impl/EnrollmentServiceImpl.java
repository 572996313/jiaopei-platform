package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.EnrollmentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.EnrollmentCreateRequest;
import com.cornerstone.module.education.dto.EnrollmentPageQuery;
import com.cornerstone.module.education.dto.EnrollmentView;
import com.cornerstone.module.education.mapper.EnrollmentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.EnrollmentService;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentMapper enrollmentMapper;
    private final InstitutionMapper institutionMapper;
    private final StudentMapper studentMapper;
    private final TeachingClassMapper teachingClassMapper;

    public EnrollmentServiceImpl(EnrollmentMapper enrollmentMapper, InstitutionMapper institutionMapper,
                                 StudentMapper studentMapper, TeachingClassMapper teachingClassMapper) {
        this.enrollmentMapper = enrollmentMapper;
        this.institutionMapper = institutionMapper;
        this.studentMapper = studentMapper;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    public List<EnrollmentEntity> listEnrollments(Long institutionId, Long classId, Long studentId) {
        LambdaQueryWrapper<EnrollmentEntity> wrapper = new LambdaQueryWrapper<EnrollmentEntity>()
            .orderByAsc(EnrollmentEntity::getInstitutionId)
            .orderByAsc(EnrollmentEntity::getClassId)
            .orderByAsc(EnrollmentEntity::getStudentId);
        if (institutionId != null) {
            wrapper.eq(EnrollmentEntity::getInstitutionId, institutionId);
        }
        if (classId != null) {
            wrapper.eq(EnrollmentEntity::getClassId, classId);
        }
        if (studentId != null) {
            wrapper.eq(EnrollmentEntity::getStudentId, studentId);
        }
        return enrollmentMapper.selectList(wrapper);
    }

    @Override
    public PageResult<EnrollmentView> pageEnrollments(EnrollmentPageQuery query) {
        LambdaQueryWrapper<EnrollmentEntity> wrapper = new LambdaQueryWrapper<EnrollmentEntity>()
            .orderByAsc(EnrollmentEntity::getInstitutionId)
            .orderByAsc(EnrollmentEntity::getClassId)
            .orderByAsc(EnrollmentEntity::getStudentId);
        if (query.getInstitutionId() != null) {
            wrapper.eq(EnrollmentEntity::getInstitutionId, query.getInstitutionId());
        }
        if (query.getClassId() != null) {
            wrapper.eq(EnrollmentEntity::getClassId, query.getClassId());
        }
        if (query.getStudentId() != null) {
            wrapper.eq(EnrollmentEntity::getStudentId, query.getStudentId());
        }
        Page<EnrollmentEntity> page = enrollmentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords()));
    }

    /** 批量回带学员与班级名称,前端分页列表无需再全量加载学员/班级做名称回显 */
    private List<EnrollmentView> toViews(List<EnrollmentEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(entities.stream()
                .map(EnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        Map<Long, TeachingClassEntity> classMap = teachingClassMapper.selectBatchIds(entities.stream()
                .map(EnrollmentEntity::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(TeachingClassEntity::getId, Function.identity()));
        return entities.stream()
            .map(entity -> {
                StudentEntity student = studentMap.get(entity.getStudentId());
                TeachingClassEntity teachingClass = classMap.get(entity.getClassId());
                return new EnrollmentView(
                    entity.getId(),
                    entity.getInstitutionId(),
                    entity.getStudentId(),
                    student == null ? null : student.getStudentNo(),
                    student == null ? null : student.getStudentName(),
                    entity.getClassId(),
                    teachingClass == null ? null : teachingClass.getClassName(),
                    entity.getEnrollmentNo(),
                    entity.getChannel(),
                    entity.getStatus(),
                    entity.getEnrolledAt(),
                    entity.getRemark()
                );
            })
            .toList();
    }

    @Override
    public EnrollmentEntity createEnrollment(EnrollmentCreateRequest request) {
        requireInstitution(request.institutionId());
        StudentEntity student = requireStudentInInstitution(request.studentId(), request.institutionId());
        TeachingClassEntity teachingClass = requireClassInInstitution(request.classId(), request.institutionId());
        if (student.getCampusId() != null && !Objects.equals(student.getCampusId(), teachingClass.getCampusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员校区与班级校区不一致");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入学日期不能晚于当前日期");
        }

        String enrollmentNo = normalize(request.enrollmentNo());
        assertUniqueEnrollmentNo(request.institutionId(), enrollmentNo);
        assertUniqueStudentClass(request.studentId(), request.classId());

        EnrollmentEntity entity = new EnrollmentEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setStudentId(request.studentId());
        entity.setClassId(request.classId());
        entity.setEnrollmentNo(enrollmentNo);
        entity.setChannel(normalizeToNull(request.channel()));
        entity.setStatus(request.status() == null || request.status().isBlank() ? "submitted" : request.status());
        entity.setEnrolledAt(request.enrolledAt() == null ? LocalDate.now() : request.enrolledAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            enrollmentMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报名记录已存在");
        }
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

    private TeachingClassEntity requireClassInInstitution(Long classId, Long institutionId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + classId);
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于该机构：" + institutionId);
        }
        return teachingClass;
    }

    private void assertUniqueEnrollmentNo(Long institutionId, String enrollmentNo) {
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<EnrollmentEntity>()
            .eq(EnrollmentEntity::getInstitutionId, institutionId)
            .eq(EnrollmentEntity::getEnrollmentNo, enrollmentNo));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下报名单号已存在：" + institutionId);
        }
    }

    private void assertUniqueStudentClass(Long studentId, Long classId) {
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<EnrollmentEntity>()
            .eq(EnrollmentEntity::getStudentId, studentId)
            .eq(EnrollmentEntity::getClassId, classId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员已存在此班级的报名记录：" + classId);
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
