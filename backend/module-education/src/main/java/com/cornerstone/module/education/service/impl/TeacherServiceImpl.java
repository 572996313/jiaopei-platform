package com.cornerstone.module.education.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherCreateRequest;
import com.cornerstone.module.education.dto.TeacherExportRow;
import com.cornerstone.module.education.dto.TeacherPageQuery;
import com.cornerstone.module.education.dto.TeacherUpdateRequest;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.TeacherService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TeacherServiceImpl implements TeacherService {

    /** 导出是不分页的全量查询，超过这个量级说明筛选条件没收紧，宁可直接拒绝也不要把内存和浏览器一起拖死 */
    private static final int MAX_EXPORT_ROWS = 5000;

    /** 教师状态未选时的回落值，与 jp_teacher.status 的列默认值一致 */
    private static final String DEFAULT_TEACHER_STATUS = "active";

    /** 用工类型未选时的回落值，与 jp_teacher.employment_type 的列默认值一致 */
    private static final String DEFAULT_EMPLOYMENT_TYPE = "full_time";

    /** 教师列表可排序字段:校区/登录账号是 ID 列,列表上显示的是名称,不参与排序 */
    private static final Map<String, SFunction<TeacherEntity, ?>> TEACHER_SORTABLE = Map.of(
        "teacherCode", TeacherEntity::getTeacherCode,
        "teacherName", TeacherEntity::getTeacherName,
        "phone", TeacherEntity::getPhone,
        "gender", TeacherEntity::getGender,
        "title", TeacherEntity::getTitle,
        "employmentType", TeacherEntity::getEmploymentType,
        "status", TeacherEntity::getStatus,
        "hiredAt", TeacherEntity::getHiredAt,
        "remark", TeacherEntity::getRemark);

    private final TeacherMapper teacherMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;
    private final UserAccountMapper userAccountMapper;
    private final DictValueValidator dictValueValidator;
    private final CodeRuleService codeRuleService;

    public TeacherServiceImpl(TeacherMapper teacherMapper, InstitutionMapper institutionMapper,
                              CampusMapper campusMapper, UserAccountMapper userAccountMapper,
                              DictValueValidator dictValueValidator, CodeRuleService codeRuleService) {
        this.teacherMapper = teacherMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
        this.userAccountMapper = userAccountMapper;
        this.dictValueValidator = dictValueValidator;
        this.codeRuleService = codeRuleService;
    }

    @Override
    public List<TeacherEntity> listTeachers(Long institutionId, Long campusId) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .orderByAsc(TeacherEntity::getInstitutionId)
            .orderByAsc(TeacherEntity::getCampusId)
            .orderByAsc(TeacherEntity::getTeacherCode);
        if (institutionId != null) {
            wrapper.eq(TeacherEntity::getInstitutionId, institutionId);
        }
        if (campusId != null) {
            wrapper.eq(TeacherEntity::getCampusId, campusId);
        }
        return teacherMapper.selectList(wrapper);
    }

    @Override
    public PageResult<TeacherEntity> pageTeachers(TeacherPageQuery query) {
        LambdaQueryWrapper<TeacherEntity> wrapper = buildWrapper(query);
        if (!PageSortSupport.apply(wrapper, query, TEACHER_SORTABLE, TeacherEntity::getId)) {
            applyDefaultOrder(wrapper);
        }
        Page<TeacherEntity> page = teacherMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords());
    }

    /** 列表、分页与导出共用默认排序 */
    private void applyDefaultOrder(LambdaQueryWrapper<TeacherEntity> wrapper) {
        wrapper.orderByAsc(TeacherEntity::getInstitutionId)
            .orderByAsc(TeacherEntity::getCampusId)
            .orderByAsc(TeacherEntity::getTeacherCode);
    }

    @Override
    public byte[] exportTeachers(TeacherPageQuery query) {
        Long total = teacherMapper.selectCount(buildWrapper(query));
        if (total == null || total == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前筛选范围下没有教师数据可导出");
        }
        if (total > MAX_EXPORT_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "待导出 " + total + " 条，超过单次上限 " + MAX_EXPORT_ROWS + " 条，请先按校区或关键字收窄范围");
        }
        LambdaQueryWrapper<TeacherEntity> exportWrapper = buildWrapper(query);
        applyDefaultOrder(exportWrapper);
        List<TeacherEntity> teachers = teacherMapper.selectList(exportWrapper);
        Map<Long, String> campusCodes = loadCampusCodes(teachers);
        Map<Long, String> usernames = loadUsernames(teachers);
        // 字典列一次取全表建映射：逐行翻译会把字典接口打爆
        Map<String, String> genderLabels = dictValueValidator.labels("gender");
        Map<String, String> employmentLabels = dictValueValidator.labels("employment_type");
        Map<String, String> statusLabels = dictValueValidator.labels("teacher_status");

        List<TeacherExportRow> rows = teachers.stream()
            .map(teacher -> new TeacherExportRow(teacher.getTeacherCode(), teacher.getTeacherName(),
                teacher.getPhone(), valueOf(campusCodes, teacher.getCampusId()),
                labelOf(genderLabels, teacher.getGender()), teacher.getTitle(),
                labelOf(employmentLabels, teacher.getEmploymentType()),
                teacher.getHiredAt() == null ? null : teacher.getHiredAt().toString(),
                labelOf(statusLabels, teacher.getStatus()), teacher.getRemark(), valueOf(usernames, teacher.getUserId())))
            .toList();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, TeacherExportRow.class).sheet("教师档案").doWrite(rows);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成教师档案导出文件失败", exception);
        }
    }

    /** 列表、分页与导出共用同一套筛选条件，三处各拼一遍迟早查出不同结果 */
    private LambdaQueryWrapper<TeacherEntity> buildWrapper(TeacherPageQuery query) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getInstitutionId() != null) {
            wrapper.eq(TeacherEntity::getInstitutionId, query.getInstitutionId());
        }
        if (query.getCampusId() != null) {
            wrapper.eq(TeacherEntity::getCampusId, query.getCampusId());
        }
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(TeacherEntity::getTeacherName, keyword)
                .or()
                .like(TeacherEntity::getTeacherCode, keyword)
                .or()
                .like(TeacherEntity::getPhone, keyword));
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(TeacherEntity::getStatus, status);
        }
        return wrapper;
    }

    /** 校区编码而不是中文名：导出要能改完导回去，中文名没有唯一性保证 */
    private Map<Long, String> loadCampusCodes(List<TeacherEntity> teachers) {
        Set<Long> campusIds = ids(teachers.stream().map(TeacherEntity::getCampusId).collect(Collectors.toSet()));
        if (campusIds.isEmpty()) {
            return Map.of();
        }
        return campusMapper.selectBatchIds(campusIds).stream()
            .collect(Collectors.toMap(CampusEntity::getId, campus -> campus.getCampusCode() == null
                ? String.valueOf(campus.getId()) : campus.getCampusCode(), (first, second) -> first));
    }

    private Map<Long, String> loadUsernames(List<TeacherEntity> teachers) {
        Set<Long> userIds = ids(teachers.stream().map(TeacherEntity::getUserId).collect(Collectors.toSet()));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserAccountEntity> accounts = new ArrayList<>(userAccountMapper.selectBatchIds(userIds));
        return accounts.stream().collect(Collectors.toMap(UserAccountEntity::getId,
            account -> account.getUsername() == null ? String.valueOf(account.getId()) : account.getUsername(),
            (first, second) -> first));
    }

    /** selectBatchIds 不接受含 null 的集合，也不接受空集合，这里一并滤掉 */
    private Set<Long> ids(Set<Long> raw) {
        return raw.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** 校区与关联账号都可以为空，而 {@code Map.of()} 的 {@code get(null)} 直接抛 NPE，取值统一走这里 */
    private static String valueOf(Map<Long, String> values, Long id) {
        return id == null ? null : values.get(id);
    }

    /** 字典列翻译成中文名；字典里没有这个值就照原样导出，宁可难看也不硬映射成合法值 */
    private static String labelOf(Map<String, String> labels, String value) {
        return value == null ? null : labels.getOrDefault(value, value);
    }

    @Override
    public TeacherEntity createTeacher(TeacherCreateRequest request) {
        requireInstitution(request.institutionId());
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), request.institutionId());
        }
        if (request.hiredAt() != null && request.hiredAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入职日期不能晚于当前日期");
        }

        String teacherCode = codeRuleService.resolveForCreate("teacher", request.teacherCode());
        String teacherName = normalize(request.teacherName());
        String phone = normalizeToNull(request.phone());
        assertUniqueTeacherCode(request.institutionId(), teacherCode, null);
        if (phone != null) {
            assertUniqueTeacherPhone(request.institutionId(), phone, null);
        }

        TeacherEntity entity = new TeacherEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTeacherCode(teacherCode);
        entity.setTeacherName(teacherName);
        entity.setPhone(phone);
        entity.setGender(dictValueValidator.validate("gender", "性别", request.gender()));
        entity.setTitle(normalizeToNull(request.title()));
        String employmentType = dictValueValidator.validate("employment_type", "用工类型", request.employmentType());
        entity.setEmploymentType(employmentType == null ? DEFAULT_EMPLOYMENT_TYPE : employmentType);
        entity.setMaxWeeklyLessons(request.maxWeeklyLessons());
        entity.setMaxDailyLessons(request.maxDailyLessons());
        String teacherStatus = dictValueValidator.validate("teacher_status", "教师状态", request.status());
        entity.setStatus(teacherStatus == null ? DEFAULT_TEACHER_STATUS : teacherStatus);
        entity.setHiredAt(request.hiredAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            teacherMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师编码或手机号已存在：" + request.institutionId());
        }
        return entity;
    }

    @Override
    public TeacherEntity updateTeacher(Long id, TeacherUpdateRequest request) {
        TeacherEntity existing = requireTeacher(id);
        if (request.campusId() != null) {
            requireCampusInInstitution(request.campusId(), existing.getInstitutionId());
        }
        if (request.hiredAt() != null && request.hiredAt().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "入职日期不能晚于当前日期");
        }

        if (request.teacherCode() != null && !request.teacherCode().isBlank()) {
            String teacherCode = normalize(request.teacherCode());
            if (!Objects.equals(existing.getTeacherCode(), teacherCode)) {
                assertUniqueTeacherCode(existing.getInstitutionId(), teacherCode, id);
                existing.setTeacherCode(teacherCode);
            }
        }
        if (request.teacherName() != null && !request.teacherName().isBlank()) {
            existing.setTeacherName(normalize(request.teacherName()));
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            String phone = normalizeToNull(request.phone());
            if (!Objects.equals(existing.getPhone(), phone)) {
                assertUniqueTeacherPhone(existing.getInstitutionId(), phone, id);
                existing.setPhone(phone);
            }
        }
        existing.setCampusId(request.campusId());
        existing.setGender(dictValueValidator.validate("gender", "性别", request.gender()));
        existing.setTitle(normalizeToNull(request.title()));
        String employmentType = dictValueValidator.validate("employment_type", "用工类型", request.employmentType());
        existing.setEmploymentType(employmentType == null ? existing.getEmploymentType() : employmentType);
        existing.setMaxWeeklyLessons(request.maxWeeklyLessons());
        existing.setMaxDailyLessons(request.maxDailyLessons());
        String status = dictValueValidator.validate("teacher_status", "教师状态", request.status());
        existing.setStatus(status == null ? existing.getStatus() : status);
        existing.setHiredAt(request.hiredAt());
        existing.setRemark(request.remark());
        try {
            teacherMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师编码或手机号已存在：" + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteTeacher(Long id) {
        requireTeacher(id);
        teacherMapper.softDeleteAndUnlink(id);
    }

    private TeacherEntity requireTeacher(Long id) {
        TeacherEntity entity = teacherMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教师不存在：" + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private void requireCampusInInstitution(Long campusId, Long institutionId) {
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + campusId);
        }
        if (!Objects.equals(campus.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "校区不属于该机构：" + institutionId);
        }
    }

    private void assertUniqueTeacherCode(Long institutionId, String teacherCode, Long excludeId) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getInstitutionId, institutionId)
            .eq(TeacherEntity::getTeacherCode, teacherCode);
        if (excludeId != null) {
            wrapper.ne(TeacherEntity::getId, excludeId);
        }
        Long count = teacherMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师编码已存在：" + institutionId);
        }
    }

    private void assertUniqueTeacherPhone(Long institutionId, String phone, Long excludeId) {
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getInstitutionId, institutionId)
            .eq(TeacherEntity::getPhone, phone);
        if (excludeId != null) {
            wrapper.ne(TeacherEntity::getId, excludeId);
        }
        Long count = teacherMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构下教师手机号已存在：" + institutionId);
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
