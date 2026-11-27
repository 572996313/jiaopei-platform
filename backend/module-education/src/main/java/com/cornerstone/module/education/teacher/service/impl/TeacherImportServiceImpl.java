package com.cornerstone.module.education.teacher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.education.support.XlsxImportSupport;
import com.cornerstone.module.education.teacher.dto.TeacherCreateRequest;
import com.cornerstone.module.education.teacher.dto.TeacherImportResult;
import com.cornerstone.module.education.teacher.dto.TeacherImportRow;
import com.cornerstone.module.education.teacher.dto.TeacherImportRowView;
import com.cornerstone.module.education.teacher.service.TeacherImportService;
import com.cornerstone.module.education.teacher.service.TeacherService;
import com.cornerstone.module.education.teacher.support.TeacherImportTemplate;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师档案批量导入：下载模板 → 预览校验 → 确认建档。三条不看这里就读不出来的口径：
 *
 * <ol>
 *   <li><b>机构只认登录态。</b>归属机构一律取 {@code AuthContext.requireUser().orgId()}，模板没有机构列、
 *       接口也不收 institutionId 参数，与底座的用户导入同口径——批量入口一旦允许指定机构，
 *       等于允许往别人机构里整批建档。</li>
 *   <li><b>只建档、不开号。</b>开登录账号是 {@code /api/system/user-import} 的职责，权限串也不同
 *       （{@code system:user:manage}）。教师页的操作员通常没有那个码，把两件事捆在一起等于逼他申请
 *       系统管理权限；绑号改由 {@code TeacherAccountLinkServiceImpl} 按手机号事后匹配。</li>
 *   <li><b>同编码跳过、手机号重复报错。</b>教师编码是本机构的业务主键，重复等于"这行已经建过了"，
 *       给提示并跳过；手机号在本机构内有唯一键，撞了就建不出来，只能报错让人改。</li>
 * </ol>
 *
 * <p>落库逐行走 {@code TeacherService.createTeacher}，不复刻一份校验：机构/校区归属、字典值、
 * 唯一性判定都在那里，两处各写一遍迟早漂移。</p>
 */
@Service
public class TeacherImportServiceImpl implements TeacherImportService {

    private static final int TEACHER_CODE_MAX = 64;
    private static final int TEACHER_NAME_MAX = 128;
    private static final int TITLE_MAX = 64;
    private static final int REMARK_MAX = 500;
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");

    /** 字典条目的启用值，与教师状态无关 */
    private static final String ACTIVE = "active";
    /** 教师状态留空时的回落值，与 jp_teacher.status 的列默认值一致 */
    private static final String DEFAULT_TEACHER_STATUS = "active";
    /** 用工类型留空时的回落值，与 jp_teacher.employment_type 的列默认值一致 */
    private static final String DEFAULT_EMPLOYMENT_TYPE = "full_time";

    private final TeacherMapper teacherMapper;
    private final CampusMapper campusMapper;
    private final TeacherService teacherService;
    private final DictValueValidator dictValueValidator;
    private final SystemDictService systemDictService;

    public TeacherImportServiceImpl(TeacherMapper teacherMapper, CampusMapper campusMapper,
                                    TeacherService teacherService, DictValueValidator dictValueValidator,
                                    SystemDictService systemDictService) {
        this.teacherMapper = teacherMapper;
        this.campusMapper = campusMapper;
        this.teacherService = teacherService;
        this.dictValueValidator = dictValueValidator;
        this.systemDictService = systemDictService;
    }

    @Override
    public byte[] buildTemplate() {
        return TeacherImportTemplate.build(activeDictNames("gender"), activeDictNames("employment_type"),
            activeDictNames("teacher_status"));
    }

    @Override
    public TeacherImportResult preview(MultipartFile file) {
        return validate(parse(file), requireInstitution());
    }

    @Override
    @Transactional
    public TeacherImportResult commit(MultipartFile file) {
        Long institutionId = requireInstitution();
        List<Plan> plans = parse(file);
        TeacherImportResult checked = validate(plans, institutionId);
        if (checked.errorCount() > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "存在 " + checked.errorCount() + " 行校验不通过，整批未导入，请修正后重新上传");
        }
        return persist(institutionId, plans, checked);
    }

    /** 归属机构只从登录态取；没绑机构的账号没有可写入的目标机构，直接拒绝而不是让他传参 */
    private Long requireInstitution() {
        return XlsxImportSupport.requireInstitution("教师档案");
    }

    // ------------------------------------------------------------------ 解析

    private List<Plan> parse(MultipartFile file) {
        List<Plan> plans = new ArrayList<>();
        List<TeacherImportRow> rows = XlsxImportSupport.readRows(file, TeacherImportRow.class);
        for (int index = 0; index < rows.size(); index++) {
            Plan plan = Plan.of(rows.get(index), index + 2);
            if (!plan.empty) {
                plans.add(plan);
            }
        }
        if (plans.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未读到有效数据行，请填写模板后重新上传");
        }
        normalizeDictColumns(plans);
        XlsxImportSupport.assertRowLimit(plans.size());
        return plans;
    }

    /**
     * 字典列容忍中文条目名：导出的 xlsx 里性别/用工类型/状态就是中文，改完直接回灌是最省事的用法，
     * 只认 item_value 等于逼操作员先把整列翻译回去。命不中的原样留给 {@code checkDict} 报错并列出可选值。
     */
    private void normalizeDictColumns(List<Plan> plans) {
        Map<String, String> genderValues = dictValueValidator.valueByLabel("gender");
        Map<String, String> employmentValues = dictValueValidator.valueByLabel("employment_type");
        Map<String, String> statusValues = dictValueValidator.valueByLabel("teacher_status");
        for (Plan plan : plans) {
            plan.gender = XlsxImportSupport.labelToValue(genderValues, plan.gender);
            plan.employmentType = XlsxImportSupport.labelToValue(employmentValues, plan.employmentType);
            plan.status = XlsxImportSupport.labelToValue(statusValues, plan.status);
        }
    }

    // ------------------------------------------------------------------ 校验

    private TeacherImportResult validate(List<Plan> plans, Long institutionId) {
        // 一次取全本机构未删除档案：活行决定"同编码跳过/手机号冲突"。
        // V106 起教师编码/手机号唯一键只约束未删除行，删除过的编码与手机号可以重新导入。
        Map<String, TeacherEntity> existingByCode = new LinkedHashMap<>();
        Map<String, Set<String>> teacherCodesByPhone = new LinkedHashMap<>();
        for (TeacherEntity teacher : teacherMapper.selectOccupations(institutionId)) {
            existingByCode.putIfAbsent(teacher.getTeacherCode(), teacher);
            if (teacher.getPhone() != null) {
                teacherCodesByPhone.computeIfAbsent(teacher.getPhone(), key -> new LinkedHashSet<>())
                    .add(teacher.getTeacherCode());
            }
        }
        Map<String, Long> campusIdsByCode = loadCampusIds(institutionId);
        Set<String> seenCodes = new LinkedHashSet<>();
        Set<String> seenPhones = new LinkedHashSet<>();

        for (Plan plan : plans) {
            checkCode(plan, existingByCode, seenCodes);
            checkName(plan);
            checkPhone(plan, teacherCodesByPhone, seenPhones);
            checkCampus(plan, campusIdsByCode);
            checkDict(plan, "gender", "性别", plan.gender);
            checkDict(plan, "employment_type", "用工类型", plan.employmentType);
            checkHiredAt(plan);
            checkStatus(plan);
            checkTitle(plan);
            checkRemark(plan);
        }
        return TeacherImportResult.of(plans.stream().map(Plan::toView).toList());
    }

    private void checkCode(Plan plan, Map<String, TeacherEntity> existingByCode, Set<String> seenCodes) {
        if (plan.teacherCode == null) {
            plan.errors.add("教师编码不能为空");
            return;
        }
        if (plan.teacherCode.length() > TEACHER_CODE_MAX) {
            plan.errors.add("教师编码长度不能超过 " + TEACHER_CODE_MAX + " 个字符");
        }
        if (!seenCodes.add(plan.teacherCode)) {
            plan.errors.add("教师编码 " + plan.teacherCode + " 在本次文件里出现多行");
            return;
        }
        TeacherEntity existing = existingByCode.get(plan.teacherCode);
        if (existing != null) {
            // 同编码 = 这行已经建过了。本模板只负责批量建档，不承担改资料，所以跳过而不是覆盖
            plan.existingTeacherId = existing.getId();
            plan.notices.add("该编码在本机构已存在，本次跳过新建（要改资料请到教师页编辑）");
        }
    }

    private void checkName(Plan plan) {
        if (plan.teacherName == null) {
            plan.errors.add("教师姓名不能为空");
        } else if (plan.teacherName.length() > TEACHER_NAME_MAX) {
            plan.errors.add("教师姓名长度不能超过 " + TEACHER_NAME_MAX + " 个字符");
        }
    }

    /**
     * 手机号选填但一旦填了就必须能用：它是「匹配账号」唯一的抓手，也是本机构的唯一键。
     * 撞在已有档案上时把对方编码带进消息，比让人回着列表一行行找快得多。
     */
    private void checkPhone(Plan plan, Map<String, Set<String>> teacherCodesByPhone, Set<String> seenPhones) {
        if (plan.phone == null) {
            plan.notices.add("未填手机号，后续无法按手机号自动匹配登录账号");
            return;
        }
        if (!PHONE.matcher(plan.phone).matches()) {
            plan.errors.add("手机号需为 11 位手机号");
            return;
        }
        if (!seenPhones.add(plan.phone)) {
            plan.errors.add("手机号 " + plan.phone + " 在本次文件里出现多行");
        }
        Set<String> owners = teacherCodesByPhone.get(plan.phone);
        // 本机构已有档案用了这个号：只有"同编码跳过"那种情况不算冲突（那本来就是不新建）
        if (owners != null && plan.existingTeacherId == null) {
            plan.errors.add("手机号 " + plan.phone + " 本机构教师 "
                + String.join("、", owners) + " 已在使用，请换一个或先改对方资料");
        }
    }

    private void checkCampus(Plan plan, Map<String, Long> campusIdsByCode) {
        if (plan.campusCode == null) {
            return;
        }
        Long campusId = campusIdsByCode.get(plan.campusCode);
        if (campusId == null) {
            plan.errors.add("未找到校区编码 " + plan.campusCode
                + "；本机构可用编码：" + String.join("、", campusIdsByCode.keySet()));
            return;
        }
        plan.campusId = campusId;
    }

    /** 字典值不合法时是行级错误而不是整批 400：一份文件里错一个性别不该让其余 200 行白填 */
    private void checkDict(Plan plan, String dictType, String fieldLabel, String value) {
        try {
            dictValueValidator.validate(dictType, fieldLabel, value);
        } catch (BusinessException exception) {
            plan.errors.add(exception.getMessage());
        }
    }

    private void checkHiredAt(Plan plan) {
        plan.hiredDate = XlsxImportSupport.parseDate(plan.hiredAt, "入职日期", plan.errors);
        if (plan.hiredDate != null && plan.hiredDate.isAfter(LocalDate.now())) {
            plan.errors.add("入职日期不能晚于当前日期");
        }
    }

    /** 状态留空按"在职"回落，与 jp_teacher.status 的列默认值一致；填了就必须命中字典启用条目 */
    private void checkStatus(Plan plan) {
        if (plan.status == null) {
            plan.status = DEFAULT_TEACHER_STATUS;
            return;
        }
        checkDict(plan, "teacher_status", "教师状态", plan.status);
    }

    private void checkTitle(Plan plan) {
        if (plan.title != null && plan.title.length() > TITLE_MAX) {
            plan.errors.add("职称长度不能超过 " + TITLE_MAX + " 个字符");
        }
    }

    private void checkRemark(Plan plan) {
        if (plan.remark != null && plan.remark.length() > REMARK_MAX) {
            plan.errors.add("备注长度不能超过 " + REMARK_MAX + " 个字符");
        }
    }

    /** 校区按编码解析：雪花校区 ID 不该让人手抄进 Excel，与底座的角色编码同一口径 */
    private Map<String, Long> loadCampusIds(Long institutionId) {
        Map<String, Long> idsByCode = new LinkedHashMap<>();
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId)
            .orderByAsc(CampusEntity::getCampusCode);
        // 导入只能落到可访问校区；其他校区的编码不出现在提示里
        CampusScopeGuard.apply(wrapper, CampusEntity::getId);
        campusMapper.selectList(wrapper)
            .forEach(campus -> idsByCode.putIfAbsent(campus.getCampusCode(), campus.getId()));
        return idsByCode;
    }

    /** 模板下拉与说明页给操作员看的是中文条目名；填中文还是代码都认，见 {@link #normalizeDictColumns} */
    private List<String> activeDictNames(String dictType) {
        return systemDictService.listItems(dictType).stream()
            .filter(item -> ACTIVE.equals(item.status()))
            .map(DictItemView::itemName)
            .toList();
    }

    // ------------------------------------------------------------------ 落库

    private TeacherImportResult persist(Long institutionId, List<Plan> plans, TeacherImportResult checked) {
        List<TeacherImportRowView> views = new ArrayList<>(plans.size());
        int created = 0;
        int skipped = 0;
        for (int index = 0; index < plans.size(); index++) {
            Plan plan = plans.get(index);
            TeacherImportRowView previewed = checked.rows().get(index);
            if (plan.existingTeacherId != null) {
                skipped++;
                views.add(previewed);
                continue;
            }
            TeacherEntity teacher = teacherService.createTeacher(new TeacherCreateRequest(
                institutionId, plan.campusId, plan.teacherCode, plan.teacherName, plan.phone,
                plan.gender, plan.title, plan.employmentType, null, null, plan.status,
                plan.hiredDate, plan.remark));
            created++;
            views.add(new TeacherImportRowView(previewed.rowNum(), previewed.teacherCode(),
                previewed.teacherName(), previewed.phone(), previewed.campusCode(), previewed.gender(),
                previewed.employmentType(), previewed.hiredAt(), previewed.status(), previewed.valid(),
                previewed.error(), previewed.notice(), teacher.getId()));
        }
        return new TeacherImportResult(checked.total(), checked.validCount(),
            checked.errorCount(), created, skipped, views);
    }

    // ------------------------------------------------------------------ 工具

    /** 取空白并把被 Excel 存成数值的串还原回数字文本；整串空白归 null */
    private static String text(String value) {
        return XlsxImportSupport.text(value);
    }

    /** 一行 Excel 在校验期间的全部中间结论：错误/提示累加、校区解析结果、同编码已有档案的 ID */
    private static final class Plan {

        private int rowNum;
        private String teacherCode;
        private String teacherName;
        private String phone;
        private String campusCode;
        private String gender;
        private String title;
        private String employmentType;
        private String hiredAt;
        private String status;
        private String remark;
        private boolean empty;
        private final List<String> errors = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();
        private Long campusId;
        private LocalDate hiredDate;
        private Long existingTeacherId;

        static Plan of(TeacherImportRow row, int rowNum) {
            Plan plan = new Plan();
            plan.rowNum = rowNum;
            plan.teacherCode = text(row.getTeacherCode());
            plan.teacherName = text(row.getTeacherName());
            plan.phone = text(row.getPhone());
            plan.campusCode = text(row.getCampusCode());
            plan.gender = text(row.getGender());
            plan.title = text(row.getTitle());
            plan.employmentType = text(row.getEmploymentType());
            plan.hiredAt = text(row.getHiredAt());
            plan.status = text(row.getStatus());
            plan.remark = text(row.getRemark());
            plan.empty = plan.teacherCode == null && plan.teacherName == null && plan.phone == null
                && plan.campusCode == null && plan.gender == null && plan.title == null
                && plan.employmentType == null && plan.hiredAt == null && plan.status == null
                && plan.remark == null;
            return plan;
        }

        TeacherImportRowView toView() {
            return new TeacherImportRowView(rowNum, teacherCode, teacherName, phone, campusCode, gender,
                employmentType == null ? DEFAULT_EMPLOYMENT_TYPE : employmentType, hiredAt,
                status == null ? DEFAULT_TEACHER_STATUS : status, errors.isEmpty(),
                errors.isEmpty() ? null : String.join("；", errors),
                notices.isEmpty() ? null : String.join("；", notices), existingTeacherId);
        }
    }
}
