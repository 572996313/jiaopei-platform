package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.EnrollmentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.dto.EnrollmentCreateRequest;
import com.cornerstone.module.education.dto.EnrollmentPageQuery;
import com.cornerstone.module.education.dto.EnrollmentView;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.EnrollmentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ClassStudentService;
import com.cornerstone.module.education.service.EnrollmentService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.education.service.support.EducationCampusScopeSupport;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    /** 报名列表可排序字段:学员/班级是 ID 列,列表上显示的是名称,不参与排序 */
    private static final Map<String, SFunction<EnrollmentEntity, ?>> ENROLLMENT_SORTABLE = Map.of(
        "enrollmentNo", EnrollmentEntity::getEnrollmentNo,
        "channel", EnrollmentEntity::getChannel,
        "status", EnrollmentEntity::getStatus,
        "enrolledAt", EnrollmentEntity::getEnrolledAt,
        "remark", EnrollmentEntity::getRemark);

    private final EnrollmentMapper enrollmentMapper;
    private final InstitutionMapper institutionMapper;
    private final StudentMapper studentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final DictValueValidator dictValueValidator;
    private final CodeRuleService codeRuleService;
    private final ClassStudentMapper classStudentMapper;
    private final ClassStudentService classStudentService;

    public EnrollmentServiceImpl(EnrollmentMapper enrollmentMapper, InstitutionMapper institutionMapper,
                                 StudentMapper studentMapper, TeachingClassMapper teachingClassMapper,
                                 DictValueValidator dictValueValidator, CodeRuleService codeRuleService,
                                 ClassStudentMapper classStudentMapper, ClassStudentService classStudentService) {
        this.enrollmentMapper = enrollmentMapper;
        this.institutionMapper = institutionMapper;
        this.studentMapper = studentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.dictValueValidator = dictValueValidator;
        this.codeRuleService = codeRuleService;
        this.classStudentMapper = classStudentMapper;
        this.classStudentService = classStudentService;
    }

    @Override
    public List<EnrollmentEntity> listEnrollments(Long institutionId, Long classId, Long studentId) {
        Long resolvedInstitutionId = InstitutionScopeGuard.resolveForQuery(institutionId, "报名");
        LambdaQueryWrapper<EnrollmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (resolvedInstitutionId != null) {
            wrapper.eq(EnrollmentEntity::getInstitutionId, resolvedInstitutionId);
        }
        if (classId != null) {
            wrapper.eq(EnrollmentEntity::getClassId, classId);
        }
        if (studentId != null) {
            wrapper.eq(EnrollmentEntity::getStudentId, studentId);
        }
        EducationCampusScopeSupport.applyClassVisible(wrapper, EnrollmentEntity::getClassId);
        applyDefaultOrder(wrapper);
        return enrollmentMapper.selectList(wrapper);
    }

    /** 列表与分页共用默认排序 */
    private void applyDefaultOrder(LambdaQueryWrapper<EnrollmentEntity> wrapper) {
        wrapper.orderByAsc(EnrollmentEntity::getInstitutionId)
            .orderByAsc(EnrollmentEntity::getClassId)
            .orderByAsc(EnrollmentEntity::getStudentId);
    }

    @Override
    public PageResult<EnrollmentView> pageEnrollments(EnrollmentPageQuery query) {
        Long resolvedInstitutionId = InstitutionScopeGuard.resolveForQuery(query.getInstitutionId(), "报名");
        LambdaQueryWrapper<EnrollmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (resolvedInstitutionId != null) {
            wrapper.eq(EnrollmentEntity::getInstitutionId, resolvedInstitutionId);
        }
        if (query.getClassId() != null) {
            wrapper.eq(EnrollmentEntity::getClassId, query.getClassId());
        }
        if (query.getStudentId() != null) {
            wrapper.eq(EnrollmentEntity::getStudentId, query.getStudentId());
        }
        EducationCampusScopeSupport.applyClassVisible(wrapper, EnrollmentEntity::getClassId);
        if (!PageSortSupport.apply(wrapper, query, ENROLLMENT_SORTABLE, EnrollmentEntity::getId)) {
            applyDefaultOrder(wrapper);
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
    @Transactional
    public EnrollmentEntity createEnrollment(EnrollmentCreateRequest request) {
        InstitutionScopeGuard.assertOwn(request.institutionId(), "报名");
        requireInstitution(request.institutionId());
        requireStudentInInstitution(request.studentId(), request.institutionId());
        TeachingClassEntity teachingClass = requireClassInInstitution(request.classId(), request.institutionId());
        CampusScopeGuard.assertCampus(teachingClass.getCampusId(), "报名");
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入学日期不能晚于当前日期");
        }

        String enrollmentNo = codeRuleService.resolveForCreate("enrollment", request.enrollmentNo());
        assertUniqueEnrollmentNo(request.institutionId(), enrollmentNo);
        assertUniqueStudentClass(request.studentId(), request.classId(), teachingClass.getClassCode());

        EnrollmentEntity entity = new EnrollmentEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setStudentId(request.studentId());
        entity.setClassId(request.classId());
        entity.setEnrollmentNo(enrollmentNo);
        entity.setChannel(dictValueValidator.validate("enrollment_channel", "报名渠道", request.channel()));
        entity.setStatus(request.status() == null || request.status().isBlank() ? "submitted" : request.status());
        entity.setEnrolledAt(request.enrolledAt() == null ? LocalDate.now() : request.enrolledAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            enrollmentMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报名记录已存在");
        }
        enrollIntoClassRoster(request, entity);
        return entity;
    }

    /** 报名即入班:报名落库后同事务写入班级名册,操作员一次办理完成;入班日期取报名日期 */
    private void enrollIntoClassRoster(EnrollmentCreateRequest request, EnrollmentEntity entity) {
        Long exists = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, request.classId())
            .eq(ClassStudentEntity::getStudentId, request.studentId()));
        if (exists != null && exists > 0) {
            // 名册已有该学员(经「分班与名册」补录先入了班)时只补报名台账,不重复入班
            return;
        }
        // 复用入班链路的唯一性/容量校验与在班人数重算;班级满员时异常抛出、同事务整体回滚,
        // 不留"报了名但没进班"的中间态
        classStudentService.createClassStudent(new ClassStudentCreateRequest(
            request.institutionId(), request.classId(), request.studentId(),
            null, entity.getEnrolledAt(), null, null));
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员不属于该机构：" + student.getStudentNo());
        }
        return student;
    }

    private TeachingClassEntity requireClassInInstitution(Long classId, Long institutionId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + classId);
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于该机构：" + teachingClass.getClassCode());
        }
        return teachingClass;
    }

    private void assertUniqueEnrollmentNo(Long institutionId, String enrollmentNo) {
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<EnrollmentEntity>()
            .eq(EnrollmentEntity::getInstitutionId, institutionId)
            .eq(EnrollmentEntity::getEnrollmentNo, enrollmentNo));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下报名单号已存在：" + enrollmentNo);
        }
    }

    private void assertUniqueStudentClass(Long studentId, Long classId, String classCode) {
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<EnrollmentEntity>()
            .eq(EnrollmentEntity::getStudentId, studentId)
            .eq(EnrollmentEntity::getClassId, classId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员已存在此班级的报名记录：" + classCode);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
