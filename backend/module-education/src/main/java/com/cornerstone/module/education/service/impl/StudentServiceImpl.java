package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.dto.StudentCreateRequest;
import com.cornerstone.module.education.dto.StudentPageQuery;
import com.cornerstone.module.education.dto.StudentOptionView;
import com.cornerstone.module.education.dto.StudentUpdateRequest;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.EnrollmentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.StudentService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.education.service.support.EducationCampusScopeSupport;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class StudentServiceImpl implements StudentService {

    /** 学员未选状态时的回落值，与 jp_student.status 的列默认值一致 */
    private static final String DEFAULT_STUDENT_STATUS = "studying";

    /**
     * 学员下拉单次返回的条数上限：一个机构的学员可以有几千条，全量下发会让前端一次性渲染同样数量的选项，
     * 所以下拉只给一屏，超出部分要求操作员输入关键字收窄。
     */
    private static final int STUDENT_OPTION_LIMIT = 30;

    /** 按 id 批量回查名称的上限：表格一屏几十行,200 条足够覆盖且不会把响应撑大 */
    private static final int STUDENT_LABEL_LIMIT = 200;

    /** 学员列表可排序字段 */
    private static final Map<String, SFunction<StudentEntity, ?>> STUDENT_SORTABLE = Map.of(
        "studentNo", StudentEntity::getStudentNo,
        "studentName", StudentEntity::getStudentName,
        "gender", StudentEntity::getGender,
        "birthday", StudentEntity::getBirthday,
        "guardianName", StudentEntity::getGuardianName,
        "guardianPhone", StudentEntity::getGuardianPhone,
        "status", StudentEntity::getStatus,
        "enrolledAt", StudentEntity::getEnrolledAt,
        "remark", StudentEntity::getRemark);

    private final StudentMapper studentMapper;
    private final InstitutionMapper institutionMapper;
    private final DictValueValidator dictValueValidator;
    private final CodeRuleService codeRuleService;
    /** 学员没有 campus_id，写侧要经班级/报名反查归属，读侧由 EducationCampusScopeSupport 用子查询收窄 */
    private final ClassStudentMapper classStudentMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final TeachingClassMapper teachingClassMapper;

    public StudentServiceImpl(StudentMapper studentMapper, InstitutionMapper institutionMapper,
                              DictValueValidator dictValueValidator, CodeRuleService codeRuleService,
                              ClassStudentMapper classStudentMapper, EnrollmentMapper enrollmentMapper,
                              TeachingClassMapper teachingClassMapper) {
        this.studentMapper = studentMapper;
        this.institutionMapper = institutionMapper;
        this.dictValueValidator = dictValueValidator;
        this.codeRuleService = codeRuleService;
        this.classStudentMapper = classStudentMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    public List<StudentEntity> listStudents(Long institutionId, String keyword) {
        Long resolvedInstitutionId = InstitutionScopeGuard.resolveForQuery(institutionId, "学员");
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<>();
        if (resolvedInstitutionId != null) {
            wrapper.eq(StudentEntity::getInstitutionId, resolvedInstitutionId);
        }
        EducationCampusScopeSupport.applyStudentVisible(wrapper);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(StudentEntity::getStudentName, keyword)
                .or()
                .like(StudentEntity::getStudentNo, keyword));
        }
        applyDefaultOrder(wrapper);
        return studentMapper.selectList(wrapper);
    }

    @Override
    public List<StudentOptionView> listStudentOptions(Long institutionId, String keyword, List<Long> ids) {
        Long resolvedInstitutionId = InstitutionScopeGuard.resolveForQuery(institutionId, "学员");
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
            .select(StudentEntity::getId, StudentEntity::getStudentNo, StudentEntity::getStudentName);
        if (resolvedInstitutionId != null) {
            wrapper.eq(StudentEntity::getInstitutionId, resolvedInstitutionId);
        }
        EducationCampusScopeSupport.applyStudentVisible(wrapper);
        List<Long> distinctIds = ids == null ? List.of()
            : ids.stream().filter(Objects::nonNull).distinct().toList();
        String normalizedKeyword = normalizeToNull(keyword);
        if (!distinctIds.isEmpty()) {
            // 按 id 回查用于给表格行补名称，关键字让位，否则传了 ids 也可能一条都查不到
            wrapper.in(StudentEntity::getId, distinctIds);
        } else if (normalizedKeyword != null) {
            wrapper.and(w -> w
                .like(StudentEntity::getStudentName, normalizedKeyword)
                .or()
                .like(StudentEntity::getStudentNo, normalizedKeyword));
        }
        applyDefaultOrder(wrapper);
        wrapper.last("LIMIT " + (distinctIds.isEmpty() ? STUDENT_OPTION_LIMIT : STUDENT_LABEL_LIMIT));
        return studentMapper.selectList(wrapper).stream()
            .map(item -> new StudentOptionView(item.getId(), item.getStudentNo(), item.getStudentName()))
            .toList();
    }

    @Override
    public PageResult<StudentEntity> pageStudents(StudentPageQuery query) {
        Long resolvedInstitutionId = InstitutionScopeGuard.resolveForQuery(query.getInstitutionId(), "学员");
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<>();
        if (resolvedInstitutionId != null) {
            wrapper.eq(StudentEntity::getInstitutionId, resolvedInstitutionId);
        }
        EducationCampusScopeSupport.applyStudentVisible(wrapper);
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(StudentEntity::getStudentName, keyword)
                .or()
                .like(StudentEntity::getStudentNo, keyword));
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(StudentEntity::getStatus, status);
        }
        if (!PageSortSupport.apply(wrapper, query, STUDENT_SORTABLE, StudentEntity::getId)) {
            applyDefaultOrder(wrapper);
        }
        Page<StudentEntity> page = studentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords());
    }

    /** 列表与分页共用默认排序 */
    private void applyDefaultOrder(LambdaQueryWrapper<StudentEntity> wrapper) {
        wrapper.orderByAsc(StudentEntity::getInstitutionId)
            .orderByAsc(StudentEntity::getStudentNo);
    }

    @Override
    public StudentEntity createStudent(StudentCreateRequest request) {
        InstitutionScopeGuard.assertOwn(request.institutionId(), "学员");
        requireInstitution(request.institutionId());
        if (request.birthday() != null && request.birthday().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "出生日期不能晚于当前日期");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入学日期不能晚于当前日期");
        }

        String studentNo = codeRuleService.resolveForCreate("student", request.studentNo());
        assertUniqueStudentNo(request.institutionId(), studentNo, null);

        StudentEntity entity = new StudentEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setStudentNo(studentNo);
        entity.setStudentName(normalize(request.studentName()));
        entity.setGender(dictValueValidator.validate("gender", "性别", request.gender()));
        entity.setBirthday(request.birthday());
        entity.setGuardianName(normalizeToNull(request.guardianName()));
        entity.setGuardianPhone(normalizeToNull(request.guardianPhone()));
        String status = dictValueValidator.validate("student_status", "学员状态", request.status());
        entity.setStatus(status == null ? DEFAULT_STUDENT_STATUS : status);
        entity.setEnrolledAt(request.enrolledAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            studentMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学员编号已存在：" + studentNo);
        }
        return entity;
    }

    @Override
    public StudentEntity updateStudent(Long id, StudentUpdateRequest request) {
        StudentEntity existing = requireStudent(id);
        InstitutionScopeGuard.assertOwn(existing.getInstitutionId(), "学员");
        EducationCampusScopeSupport.assertStudentWritable(
            classStudentMapper, enrollmentMapper, teachingClassMapper, id);
        if (request.birthday() != null && request.birthday().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "出生日期不能晚于当前日期");
        }
        if (request.enrolledAt() != null && request.enrolledAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入学日期不能晚于当前日期");
        }

        if (request.studentNo() != null && !request.studentNo().isBlank()) {
            String studentNo = normalize(request.studentNo());
            if (!Objects.equals(existing.getStudentNo(), studentNo)) {
                assertUniqueStudentNo(existing.getInstitutionId(), studentNo, id);
                existing.setStudentNo(studentNo);
            }
        }
        if (request.studentName() != null && !request.studentName().isBlank()) {
            existing.setStudentName(normalize(request.studentName()));
        }
        existing.setGender(dictValueValidator.validate("gender", "性别", request.gender()));
        existing.setBirthday(request.birthday());
        existing.setGuardianName(normalizeToNull(request.guardianName()));
        existing.setGuardianPhone(normalizeToNull(request.guardianPhone()));
        String status = dictValueValidator.validate("student_status", "学员状态", request.status());
        existing.setStatus(status == null ? existing.getStatus() : status);
        existing.setEnrolledAt(request.enrolledAt());
        existing.setRemark(request.remark());
        try {
            studentMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学员编号已存在：" + existing.getStudentNo());
        }
        return existing;
    }

    @Override
    public void deleteStudent(Long id) {
        StudentEntity existing = requireStudent(id);
        InstitutionScopeGuard.assertOwn(existing.getInstitutionId(), "学员");
        EducationCampusScopeSupport.assertStudentWritable(
            classStudentMapper, enrollmentMapper, teachingClassMapper, id);
        studentMapper.deleteById(id);
    }

    private StudentEntity requireStudent(Long id) {
        StudentEntity entity = studentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private void assertUniqueStudentNo(Long institutionId, String studentNo, Long excludeId) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
            .eq(StudentEntity::getInstitutionId, institutionId)
            .eq(StudentEntity::getStudentNo, studentNo);
        if (excludeId != null) {
            wrapper.ne(StudentEntity::getId, excludeId);
        }
        Long count = studentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下学员编号已存在：" + studentNo);
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
