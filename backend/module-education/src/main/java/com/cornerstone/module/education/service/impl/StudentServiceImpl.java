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
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.service.StudentService;
import com.cornerstone.module.education.service.support.DictValueValidator;
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

    public StudentServiceImpl(StudentMapper studentMapper, InstitutionMapper institutionMapper,
                              DictValueValidator dictValueValidator, CodeRuleService codeRuleService) {
        this.studentMapper = studentMapper;
        this.institutionMapper = institutionMapper;
        this.dictValueValidator = dictValueValidator;
        this.codeRuleService = codeRuleService;
    }

    @Override
    public List<StudentEntity> listStudents(Long institutionId, String keyword) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<>();
        if (institutionId != null) {
            wrapper.eq(StudentEntity::getInstitutionId, institutionId);
        }
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
    public List<StudentOptionView> listStudentOptions(Long institutionId, String keyword) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
            .select(StudentEntity::getId, StudentEntity::getStudentNo, StudentEntity::getStudentName);
        if (institutionId != null) {
            wrapper.eq(StudentEntity::getInstitutionId, institutionId);
        }
        String normalizedKeyword = normalizeToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w
                .like(StudentEntity::getStudentName, normalizedKeyword)
                .or()
                .like(StudentEntity::getStudentNo, normalizedKeyword));
        }
        applyDefaultOrder(wrapper);
        wrapper.last("LIMIT " + STUDENT_OPTION_LIMIT);
        return studentMapper.selectList(wrapper).stream()
            .map(item -> new StudentOptionView(item.getId(), item.getStudentNo(), item.getStudentName()))
            .toList();
    }

    @Override
    public PageResult<StudentEntity> pageStudents(StudentPageQuery query) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getInstitutionId() != null) {
            wrapper.eq(StudentEntity::getInstitutionId, query.getInstitutionId());
        }
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
        requireStudent(id);
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
