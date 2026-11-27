package com.cornerstone.module.education.student.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.dto.XlsxImportRowView;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.student.dto.StudentCreateRequest;
import com.cornerstone.module.education.student.dto.StudentImportRow;
import com.cornerstone.module.education.student.service.StudentImportService;
import com.cornerstone.module.education.student.service.StudentService;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.education.support.XlsxImportSupport;
import com.cornerstone.module.education.support.XlsxImportTemplate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学员档案批量导入。
 *
 * <ol>
 *   <li><b>机构只认登录态。</b>与教师导入、底座账号导入同一口径：模板没有机构列、接口不收 institutionId。</li>
 *   <li><b>学号可留空。</b>批量/模板路径是"留空即补号"的合法入口（PC 单个新增仍必须让客户看见编号）；
 *       有学号时跳过同号，没有学号就逐行走编号规则。</li>
 *   <li><b>不建登录账号。</b>学员/家长的小程序账号走自助注册或用户管理，导入只管档案。</li>
 * </ol>
 *
 * <p>落库逐行走 {@code StudentService.createStudent}，不复刻一份校验：出生/入学日期、字典值、
 * 编号唯一性都在那里。预览只读不写。</p>
 */
@Service
public class StudentImportServiceImpl implements StudentImportService {

    private static final int STUDENT_NO_MAX = 64;
    private static final int STUDENT_NAME_MAX = 128;
    private static final int GUARDIAN_NAME_MAX = 128;
    private static final int GUARDIAN_PHONE_MAX = 32;
    private static final int REMARK_MAX = 500;

    /** 字典条目的启用值 */
    private static final String ACTIVE = "active";
    /** 学员状态留空时的回落值，与 jp_student.status 的列默认值一致 */
    private static final String DEFAULT_STUDENT_STATUS = "studying";

    private final StudentMapper studentMapper;
    private final StudentService studentService;
    private final DictValueValidator dictValueValidator;
    private final SystemDictService systemDictService;

    public StudentImportServiceImpl(StudentMapper studentMapper, StudentService studentService,
                                    DictValueValidator dictValueValidator,
                                    SystemDictService systemDictService) {
        this.studentMapper = studentMapper;
        this.studentService = studentService;
        this.dictValueValidator = dictValueValidator;
        this.systemDictService = systemDictService;
    }

    @Override
    public byte[] buildTemplate() {
        return XlsxImportTemplate.student(activeDictNames("gender"), activeDictNames("student_status"));
    }

    @Override
    public XlsxImportResult preview(MultipartFile file) {
        return validate(parse(file), XlsxImportSupport.requireInstitution("学员档案"));
    }

    @Override
    @Transactional
    public XlsxImportResult commit(MultipartFile file) {
        Long institutionId = XlsxImportSupport.requireInstitution("学员档案");
        List<Plan> plans = parse(file);
        XlsxImportResult checked = validate(plans, institutionId);
        if (checked.errorCount() > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "存在 " + checked.errorCount() + " 行校验不通过，整批未导入，请修正后重新上传");
        }
        return persist(institutionId, plans);
    }

    // ------------------------------------------------------------------ 解析

    private List<Plan> parse(MultipartFile file) {
        List<Plan> plans = new ArrayList<>();
        List<StudentImportRow> rows = XlsxImportSupport.readRows(file, StudentImportRow.class);
        for (int index = 0; index < rows.size(); index++) {
            Plan plan = Plan.of(rows.get(index), index + 2);
            if (!plan.empty) {
                plans.add(plan);
            }
        }
        if (plans.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未读到有效数据行，请填写模板后重新上传");
        }
        XlsxImportSupport.assertRowLimit(plans.size());
        normalizeDictColumns(plans);
        return plans;
    }

    /** 字典列容忍中文条目名：导出文件里就是中文，改完直接回灌是最省事的用法 */
    private void normalizeDictColumns(List<Plan> plans) {
        Map<String, String> genderValues = dictValueValidator.valueByLabel("gender");
        Map<String, String> statusValues = dictValueValidator.valueByLabel("student_status");
        for (Plan plan : plans) {
            plan.gender = XlsxImportSupport.labelToValue(genderValues, plan.gender);
            plan.status = XlsxImportSupport.labelToValue(statusValues, plan.status);
        }
    }

    // ------------------------------------------------------------------ 校验

    private XlsxImportResult validate(List<Plan> plans, Long institutionId) {
        Map<String, StudentEntity> existingByNo = loadExistingByNo(institutionId, plans);
        Set<String> seenNos = new LinkedHashSet<>();
        for (Plan plan : plans) {
            checkStudentNo(plan, existingByNo, seenNos);
            checkStudentName(plan);
            checkDict(plan, "gender", "性别", plan.gender);
            checkBirthday(plan);
            checkGuardianName(plan);
            checkGuardianPhone(plan);
            checkStatus(plan);
            checkEnrolledAt(plan);
            checkRemark(plan);
            plan.view = plan.toView();
        }
        return XlsxImportResult.preview(plans.stream().map(plan -> plan.view).toList());
    }

    /** 只回查本文件里出现过的学号：学员可达数千条，不能像教师那样把全机构档案拉回来建 map */
    private Map<String, StudentEntity> loadExistingByNo(Long institutionId, List<Plan> plans) {
        Set<String> codes = new LinkedHashSet<>();
        for (Plan plan : plans) {
            if (plan.studentNo != null) {
                codes.add(plan.studentNo);
            }
        }
        Map<String, StudentEntity> existing = new LinkedHashMap<>();
        if (codes.isEmpty()) {
            return existing;
        }
        studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getInstitutionId, institutionId)
                .in(StudentEntity::getStudentNo, codes))
            .forEach(student -> existing.putIfAbsent(student.getStudentNo(), student));
        return existing;
    }

    private void checkStudentNo(Plan plan, Map<String, StudentEntity> existingByNo, Set<String> seenNos) {
        if (plan.studentNo == null) {
            // 留空由编号规则补号，预览阶段无法知道最终号，结果里会回填
            return;
        }
        if (plan.studentNo.length() > STUDENT_NO_MAX) {
            plan.errors.add("学号长度不能超过 " + STUDENT_NO_MAX + " 个字符");
        }
        if (!seenNos.add(plan.studentNo)) {
            plan.errors.add("学号 " + plan.studentNo + " 在本次文件里出现多行");
            return;
        }
        StudentEntity existing = existingByNo.get(plan.studentNo);
        if (existing != null) {
            // 同学号 = 这行已经建过了。本模板只负责批量建档，不承担改资料，所以跳过而不是覆盖
            plan.existingId = existing.getId();
            plan.notices.add("该学号在本机构已存在，本次跳过新建（要改资料请到学员页编辑）");
        }
    }

    private void checkStudentName(Plan plan) {
        if (plan.studentName == null) {
            plan.errors.add("学员姓名不能为空");
        } else if (plan.studentName.length() > STUDENT_NAME_MAX) {
            plan.errors.add("学员姓名长度不能超过 " + STUDENT_NAME_MAX + " 个字符");
        }
    }

    /** 字典值不合法时是行级错误而不是整批 400：一份文件里错一个性别不该让其余 200 行白填 */
    private void checkDict(Plan plan, String dictType, String fieldLabel, String value) {
        try {
            dictValueValidator.validate(dictType, fieldLabel, value);
        } catch (BusinessException exception) {
            plan.errors.add(exception.getMessage());
        }
    }

    private void checkBirthday(Plan plan) {
        plan.birthday = XlsxImportSupport.parseDate(plan.birthdayText, "出生日期", plan.errors);
        if (plan.birthday != null && plan.birthday.isAfter(LocalDate.now())) {
            plan.errors.add("出生日期不能晚于当前日期");
        }
    }

    private void checkGuardianName(Plan plan) {
        if (plan.guardianName != null && plan.guardianName.length() > GUARDIAN_NAME_MAX) {
            plan.errors.add("监护人姓名长度不能超过 " + GUARDIAN_NAME_MAX + " 个字符");
        }
    }

    private void checkGuardianPhone(Plan plan) {
        if (plan.guardianPhone != null && plan.guardianPhone.length() > GUARDIAN_PHONE_MAX) {
            plan.errors.add("监护人电话长度不能超过 " + GUARDIAN_PHONE_MAX + " 个字符");
        }
    }

    /** 状态留空按"在读"回落，与 jp_student.status 的列默认值一致；填了就必须命中字典启用条目 */
    private void checkStatus(Plan plan) {
        if (plan.status == null) {
            plan.status = DEFAULT_STUDENT_STATUS;
            return;
        }
        checkDict(plan, "student_status", "学员状态", plan.status);
    }

    private void checkEnrolledAt(Plan plan) {
        plan.enrolledAt = XlsxImportSupport.parseDate(plan.enrolledAtText, "入学日期", plan.errors);
        if (plan.enrolledAt != null && plan.enrolledAt.isAfter(LocalDate.now())) {
            plan.errors.add("入学日期不能晚于当前日期");
        }
    }

    private void checkRemark(Plan plan) {
        if (plan.remark != null && plan.remark.length() > REMARK_MAX) {
            plan.errors.add("备注长度不能超过 " + REMARK_MAX + " 个字符");
        }
    }

    /** 模板下拉与说明页给操作员看的是中文条目名 */
    private List<String> activeDictNames(String dictType) {
        return systemDictService.listItems(dictType).stream()
            .filter(item -> ACTIVE.equals(item.status()))
            .map(DictItemView::itemName)
            .toList();
    }

    // ------------------------------------------------------------------ 落库

    private XlsxImportResult persist(Long institutionId, List<Plan> plans) {
        int created = 0;
        int skipped = 0;
        for (Plan plan : plans) {
            if (plan.existingId != null) {
                skipped++;
                continue;
            }
            StudentEntity student = studentService.createStudent(new StudentCreateRequest(
                institutionId, plan.studentNo, plan.studentName, plan.gender, plan.birthday,
                plan.guardianName, plan.guardianPhone, plan.status, plan.enrolledAt, plan.remark));
            // 学号留空时编号是导入阶段才生成的，必须回填给操作员看
            plan.view.cells().put("studentNo", student.getStudentNo());
            created++;
        }
        return XlsxImportResult.committed(created, skipped,
            plans.stream().map(plan -> plan.view).toList());
    }

    // ------------------------------------------------------------------ 中间行

    /** 一行 Excel 在校验期间的全部中间结论 */
    private static final class Plan {

        private final int rowNum;
        private String studentNo;
        private String studentName;
        private String gender;
        private String birthdayText;
        private String guardianName;
        private String guardianPhone;
        private String status;
        private String enrolledAtText;
        private String remark;
        private final boolean empty;
        private final List<String> errors = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();
        private LocalDate birthday;
        private LocalDate enrolledAt;
        private Long existingId;
        private XlsxImportRowView view;

        private Plan(int rowNum, StudentImportRow row) {
            this.rowNum = rowNum;
            this.studentNo = XlsxImportSupport.text(row.getStudentNo());
            this.studentName = XlsxImportSupport.text(row.getStudentName());
            this.gender = XlsxImportSupport.text(row.getGender());
            this.birthdayText = XlsxImportSupport.text(row.getBirthday());
            this.guardianName = XlsxImportSupport.text(row.getGuardianName());
            this.guardianPhone = XlsxImportSupport.text(row.getGuardianPhone());
            this.status = XlsxImportSupport.text(row.getStatus());
            this.enrolledAtText = XlsxImportSupport.text(row.getEnrolledAt());
            this.remark = XlsxImportSupport.text(row.getRemark());
            this.empty = studentNo == null && studentName == null && gender == null
                && birthdayText == null && guardianName == null && guardianPhone == null
                && status == null && enrolledAtText == null && remark == null;
        }

        private static Plan of(StudentImportRow row, int rowNum) {
            return new Plan(rowNum, row);
        }

        private XlsxImportRowView toView() {
            Map<String, String> cells = new LinkedHashMap<>();
            cells.put("studentNo", studentNo);
            cells.put("studentName", studentName);
            cells.put("gender", gender);
            cells.put("birthday", birthdayText);
            cells.put("guardianName", guardianName);
            cells.put("guardianPhone", guardianPhone);
            cells.put("status", status);
            cells.put("enrolledAt", enrolledAtText);
            cells.put("remark", remark);
            return new XlsxImportRowView(rowNum, errors.isEmpty(),
                errors.isEmpty() ? null : String.join("；", errors),
                notices.isEmpty() ? null : String.join("；", notices), cells);
        }
    }
}
