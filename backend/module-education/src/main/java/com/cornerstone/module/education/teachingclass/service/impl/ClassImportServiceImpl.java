package com.cornerstone.module.education.teachingclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.dto.XlsxImportRowView;
import com.cornerstone.module.education.mapper.ClassTypeMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.support.XlsxImportSupport;
import com.cornerstone.module.education.support.XlsxImportTemplate;
import com.cornerstone.module.education.teachingclass.dto.ClassImportRow;
import com.cornerstone.module.education.teachingclass.dto.TeachingClassCreateRequest;
import com.cornerstone.module.education.teachingclass.service.ClassImportService;
import com.cornerstone.module.education.teachingclass.service.TeachingClassService;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 班级档案批量导入。
 *
 * <ol>
 *   <li><b>机构只认登录态</b>，校区必须在本账号可访问范围内，与单个新增的口径一致。</li>
 *   <li><b>班级编码/名称的唯一键是「校区ID + 业务键」</b>（V105 的守卫键），不是机构级：
 *       判重、跳过、重名报错全部按校区收窄，跨校区可以有同编码、同名的班。</li>
 *   <li><b>只建班级主档</b>，不建校区/学期/课程/班型/教师；那些编码先查好再导入，查不到就是行级错误。</li>
 *   <li><b>班级状态不入字典</b>（代码判定值），模板与导入只认 planning/active/inactive/closed，
 *       中文含义写在说明页，导入侧不做中文翻译。</li>
 * </ol>
 *
 * <p>落库逐行走 {@code TeachingClassService.createClass}，主讲关系、班级变动通知等副作用都在那里，
 * 不在这里复制一份。</p>
 */
@Service
public class ClassImportServiceImpl implements ClassImportService {

    private static final int CLASS_CODE_MAX = 64;
    private static final int CLASS_NAME_MAX = 128;
    private static final int REMARK_MAX = 500;

    /** 班级状态是代码判定值，不进字典；留空按规划中，与 jp_class.status 的列默认值一致 */
    private static final String DEFAULT_CLASS_STATUS = "planning";
    private static final Set<String> CLASS_STATUSES = Set.of("planning", "active", "inactive", "closed");

    private final TeachingClassMapper teachingClassMapper;
    private final CampusMapper campusMapper;
    private final TermMapper termMapper;
    private final CourseMapper courseMapper;
    private final ClassTypeMapper classTypeMapper;
    private final TeacherMapper teacherMapper;
    private final TeachingClassService teachingClassService;

    public ClassImportServiceImpl(TeachingClassMapper teachingClassMapper, CampusMapper campusMapper,
                                  TermMapper termMapper, CourseMapper courseMapper,
                                  ClassTypeMapper classTypeMapper, TeacherMapper teacherMapper,
                                  TeachingClassService teachingClassService) {
        this.teachingClassMapper = teachingClassMapper;
        this.campusMapper = campusMapper;
        this.termMapper = termMapper;
        this.courseMapper = courseMapper;
        this.classTypeMapper = classTypeMapper;
        this.teacherMapper = teacherMapper;
        this.teachingClassService = teachingClassService;
    }

    @Override
    public byte[] buildTemplate() {
        return XlsxImportTemplate.teachingClass();
    }

    @Override
    public XlsxImportResult preview(MultipartFile file) {
        return validate(parse(file), XlsxImportSupport.requireInstitution("班级档案"));
    }

    @Override
    @Transactional
    public XlsxImportResult commit(MultipartFile file) {
        Long institutionId = XlsxImportSupport.requireInstitution("班级档案");
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
        List<ClassImportRow> rows = XlsxImportSupport.readRows(file, ClassImportRow.class);
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
        return plans;
    }

    // ------------------------------------------------------------------ 校验

    private XlsxImportResult validate(List<Plan> plans, Long institutionId) {
        Map<String, CampusEntity> campusByCode = loadCampuses(institutionId);
        Map<String, TermEntity> termByCode = loadTerms(institutionId, plans);
        Map<String, CourseEntity> courseByCode = loadCourses(institutionId, plans);
        Map<String, ClassTypeEntity> classTypeByCode = loadClassTypes(institutionId, plans);
        Map<String, TeacherEntity> teacherByCode = loadTeachers(institutionId, plans);
        Map<String, TeachingClassEntity> existingByCampusAndCode = loadExistingClasses(institutionId, plans);
        Set<String> seenCampusCodes = new LinkedHashSet<>();
        Set<String> seenCampusNames = new LinkedHashSet<>();

        for (Plan plan : plans) {
            checkCampus(plan, campusByCode);
            checkCode(plan, existingByCampusAndCode, seenCampusCodes);
            checkName(plan, existingByCampusAndCode, seenCampusNames);
            checkTerm(plan, termByCode);
            checkCourse(plan, courseByCode);
            checkClassType(plan, classTypeByCode);
            checkTeacher(plan, teacherByCode);
            checkCapacity(plan);
            checkWeeklyLessons(plan);
            checkOpenedAt(plan);
            checkStatus(plan);
            checkRemark(plan);
            plan.view = plan.toView();
        }
        return XlsxImportResult.preview(plans.stream().map(plan -> plan.view).toList());
    }

    /**
     * 校区是必填列，也是唯一"列全部可用编码"的依据：数量少、写错时最有帮助。
     * 受校区范围限制的账号看不到别的校区，编码也不出现在提示里。
     */
    private Map<String, CampusEntity> loadCampuses(Long institutionId) {
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId)
            .orderByAsc(CampusEntity::getCampusCode);
        CampusScopeGuard.apply(wrapper, CampusEntity::getId);
        return campusMapper.selectList(wrapper).stream()
            .collect(Collectors.toMap(CampusEntity::getCampusCode, Function.identity(),
                (first, second) -> first, LinkedHashMap::new));
    }

    private Map<String, TermEntity> loadTerms(Long institutionId, List<Plan> plans) {
        Set<String> codes = collectCodes(plans, plan -> plan.termCode);
        if (codes.isEmpty()) {
            return Map.of();
        }
        return termMapper.selectList(new LambdaQueryWrapper<TermEntity>()
                .eq(TermEntity::getInstitutionId, institutionId)
                .in(TermEntity::getTermCode, codes))
            .stream()
            .collect(Collectors.toMap(TermEntity::getTermCode, Function.identity(),
                (first, second) -> first));
    }

    private Map<String, CourseEntity> loadCourses(Long institutionId, List<Plan> plans) {
        Set<String> codes = collectCodes(plans, plan -> plan.courseCode);
        if (codes.isEmpty()) {
            return Map.of();
        }
        return courseMapper.selectList(new LambdaQueryWrapper<CourseEntity>()
                .eq(CourseEntity::getInstitutionId, institutionId)
                .in(CourseEntity::getCourseCode, codes))
            .stream()
            .collect(Collectors.toMap(CourseEntity::getCourseCode, Function.identity(),
                (first, second) -> first));
    }

    private Map<String, ClassTypeEntity> loadClassTypes(Long institutionId, List<Plan> plans) {
        Set<String> codes = collectCodes(plans, plan -> plan.classTypeCode);
        if (codes.isEmpty()) {
            return Map.of();
        }
        return classTypeMapper.selectList(new LambdaQueryWrapper<ClassTypeEntity>()
                .eq(ClassTypeEntity::getInstitutionId, institutionId)
                .in(ClassTypeEntity::getTypeCode, codes))
            .stream()
            .collect(Collectors.toMap(ClassTypeEntity::getTypeCode, Function.identity(),
                (first, second) -> first));
    }

    private Map<String, TeacherEntity> loadTeachers(Long institutionId, List<Plan> plans) {
        Set<String> codes = collectCodes(plans, plan -> plan.teacherCode);
        if (codes.isEmpty()) {
            return Map.of();
        }
        return teacherMapper.selectList(new LambdaQueryWrapper<TeacherEntity>()
                .eq(TeacherEntity::getInstitutionId, institutionId)
                .in(TeacherEntity::getTeacherCode, codes))
            .stream()
            .collect(Collectors.toMap(TeacherEntity::getTeacherCode, Function.identity(),
                (first, second) -> first));
    }

    /** 只回查本文件涉及的编码/名称：班级可以有几百个，没必要全量拉回本机 */
    private Map<String, TeachingClassEntity> loadExistingClasses(Long institutionId, List<Plan> plans) {
        Set<String> codes = collectCodes(plans, plan -> plan.classCode);
        Set<String> names = collectCodes(plans, plan -> plan.className);
        Map<String, TeachingClassEntity> existing = new LinkedHashMap<>();
        if (codes.isEmpty() && names.isEmpty()) {
            return existing;
        }
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getInstitutionId, institutionId);
        if (codes.isEmpty()) {
            wrapper.in(TeachingClassEntity::getClassName, names);
        } else if (names.isEmpty()) {
            wrapper.in(TeachingClassEntity::getClassCode, codes);
        } else {
            wrapper.and(nested -> nested
                .in(TeachingClassEntity::getClassCode, codes)
                .or()
                .in(TeachingClassEntity::getClassName, names));
        }
        for (TeachingClassEntity teachingClass : teachingClassMapper.selectList(wrapper)) {
            existing.putIfAbsent(scopedKey(teachingClass.getCampusId(), teachingClass.getClassCode()), teachingClass);
            existing.putIfAbsent(scopedKey(teachingClass.getCampusId(), teachingClass.getClassName()), teachingClass);
        }
        return existing;
    }

    private Set<String> collectCodes(List<Plan> plans, Function<Plan, String> getter) {
        Set<String> codes = new LinkedHashSet<>();
        for (Plan plan : plans) {
            String code = getter.apply(plan);
            if (code != null) {
                codes.add(code);
            }
        }
        return codes;
    }

    private void checkCampus(Plan plan, Map<String, CampusEntity> campusByCode) {
        if (plan.campusCode == null) {
            plan.errors.add("校区编码不能为空");
            return;
        }
        CampusEntity campus = campusByCode.get(plan.campusCode);
        if (campus == null) {
            plan.errors.add("未找到校区编码 " + plan.campusCode
                + "；本机构可用编码：" + String.join("、", campusByCode.keySet()));
            return;
        }
        plan.campusId = campus.getId();
    }

    private void checkCode(Plan plan, Map<String, TeachingClassEntity> existingByCampusAndCode,
                           Set<String> seenCampusCodes) {
        if (plan.classCode == null) {
            // 留空由编号规则补号，预览阶段无法知道最终号，结果里会回填
            return;
        }
        if (plan.classCode.length() > CLASS_CODE_MAX) {
            plan.errors.add("班级编码长度不能超过 " + CLASS_CODE_MAX + " 个字符");
        }
        if (plan.campusId == null) {
            // 校区没解析出来时按编码本身判文件内重复，不给"已存在"结论（无法按校区比对）
            return;
        }
        String key = scopedKey(plan.campusId, plan.classCode);
        if (!seenCampusCodes.add(key)) {
            plan.errors.add("班级编码 " + plan.classCode + " 在本文件同一校区下出现多行");
            return;
        }
        TeachingClassEntity existing = existingByCampusAndCode.get(key);
        if (existing != null) {
            // 同校区同编码 = 这个班已经建过了。本模板只负责批量建档，不承担改资料，所以跳过而不是覆盖
            plan.existingClassId = existing.getId();
            plan.notices.add("该校区下班级编码 " + plan.classCode + " 已存在，本次跳过新建（要改资料请到班级页编辑）");
        }
    }

    private void checkName(Plan plan, Map<String, TeachingClassEntity> existingByCampusAndCode,
                           Set<String> seenCampusNames) {
        if (plan.className == null) {
            plan.errors.add("班级名称不能为空");
            return;
        }
        if (plan.className.length() > CLASS_NAME_MAX) {
            plan.errors.add("班级名称长度不能超过 " + CLASS_NAME_MAX + " 个字符");
        }
        if (plan.campusId == null) {
            return;
        }
        String key = scopedKey(plan.campusId, plan.className);
        if (!seenCampusNames.add(key)) {
            plan.errors.add("班级名称 " + plan.className + " 在本文件同一校区下出现多行");
            return;
        }
        TeachingClassEntity existing = existingByCampusAndCode.get(key);
        if (existing != null && !existing.getId().equals(plan.existingClassId)) {
            // 同校区重名是硬冲突（uk_jp_class_name_guard）：不能像同编码那样"跳过"，必须让人改名
            plan.errors.add("该校区下班级名称「" + plan.className + "」已被班级编码 "
                + existing.getClassCode() + " 使用，请换一个名称");
        }
    }

    private void checkTerm(Plan plan, Map<String, TermEntity> termByCode) {
        if (plan.termCode == null) {
            return;
        }
        TermEntity term = termByCode.get(plan.termCode);
        if (term == null) {
            plan.errors.add("未找到学期编码 " + plan.termCode + "，请到学期维护页核对");
            return;
        }
        plan.termId = term.getId();
    }

    private void checkCourse(Plan plan, Map<String, CourseEntity> courseByCode) {
        if (plan.courseCode == null) {
            return;
        }
        CourseEntity course = courseByCode.get(plan.courseCode);
        if (course == null) {
            plan.errors.add("未找到课程编码 " + plan.courseCode + "，请到课程页核对");
            return;
        }
        plan.courseId = course.getId();
    }

    private void checkClassType(Plan plan, Map<String, ClassTypeEntity> classTypeByCode) {
        if (plan.classTypeCode == null) {
            return;
        }
        ClassTypeEntity classType = classTypeByCode.get(plan.classTypeCode);
        if (classType == null) {
            plan.errors.add("未找到班型编码 " + plan.classTypeCode + "，请到班型维护页核对");
            return;
        }
        plan.classTypeId = classType.getId();
    }

    private void checkTeacher(Plan plan, Map<String, TeacherEntity> teacherByCode) {
        if (plan.teacherCode == null) {
            return;
        }
        TeacherEntity teacher = teacherByCode.get(plan.teacherCode);
        if (teacher == null) {
            plan.errors.add("未找到主讲教师编码 " + plan.teacherCode + "，请到教师页核对");
            return;
        }
        plan.teacherId = teacher.getId();
    }

    private void checkCapacity(Plan plan) {
        plan.capacity = parseInteger(plan, plan.capacityRaw, "容量上限", 0);
    }

    private void checkWeeklyLessons(Plan plan) {
        plan.weeklyLessons = parseInteger(plan, plan.weeklyLessonsRaw, "每周应上节数", 1);
    }

    private Integer parseInteger(Plan plan, String raw, String label, int min) {
        if (raw == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < min) {
                plan.errors.add(label + "必须大于或等于 " + min);
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            plan.errors.add(label + "需为整数：" + raw);
            return null;
        }
    }

    private void checkOpenedAt(Plan plan) {
        // 开班日期允许未来（规划中的班），不能像入职/入学日期那样拦今天之后
        plan.openedAt = XlsxImportSupport.parseDate(plan.openedAtText, "开班日期", plan.errors);
    }

    private void checkStatus(Plan plan) {
        if (plan.status == null) {
            plan.status = DEFAULT_CLASS_STATUS;
            return;
        }
        if (!CLASS_STATUSES.contains(plan.status)) {
            plan.errors.add("状态取值无效，可选值：" + String.join("/", CLASS_STATUSES));
        }
    }

    private void checkRemark(Plan plan) {
        if (plan.remark != null && plan.remark.length() > REMARK_MAX) {
            plan.errors.add("备注长度不能超过 " + REMARK_MAX + " 个字符");
        }
    }

    // ------------------------------------------------------------------ 落库

    private XlsxImportResult persist(Long institutionId, List<Plan> plans) {
        int created = 0;
        int skipped = 0;
        for (Plan plan : plans) {
            if (plan.existingClassId != null) {
                skipped++;
                continue;
            }
            TeachingClassEntity teachingClass = teachingClassService.createClass(new TeachingClassCreateRequest(
                institutionId, plan.campusId, plan.termId, plan.courseId, plan.classTypeId,
                plan.classCode, plan.className, plan.teacherId, plan.capacity, plan.weeklyLessons,
                "weekly", plan.weeklyLessons, null, null, plan.status, plan.openedAt, plan.remark));
            // 班级编码留空时编号是导入阶段才生成的，必须回填给操作员看
            plan.view.cells().put("classCode", teachingClass.getClassCode());
            created++;
        }
        return XlsxImportResult.committed(created, skipped,
            plans.stream().map(plan -> plan.view).toList());
    }

    // ------------------------------------------------------------------ 工具

    /** 唯一键是「校区ID + 业务键」，内存里的判重也必须带校区，否则跨校区重码会被误判 */
    private static String scopedKey(Long campusId, String businessKey) {
        return campusId + "#" + businessKey;
    }

    /** 一行 Excel 在校验期间的全部中间结论 */
    private static final class Plan {

        private final int rowNum;
        private String classCode;
        private String className;
        private String campusCode;
        private String termCode;
        private String courseCode;
        private String classTypeCode;
        private String teacherCode;
        private String capacityRaw;
        private String weeklyLessonsRaw;
        private String openedAtText;
        private String status;
        private String remark;
        private final boolean empty;
        private final List<String> errors = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();
        private Long campusId;
        private Long termId;
        private Long courseId;
        private Long classTypeId;
        private Long teacherId;
        private Integer capacity;
        private Integer weeklyLessons;
        private LocalDate openedAt;
        private Long existingClassId;
        private XlsxImportRowView view;

        private Plan(int rowNum, ClassImportRow row) {
            this.rowNum = rowNum;
            this.classCode = XlsxImportSupport.text(row.getClassCode());
            this.className = XlsxImportSupport.text(row.getClassName());
            this.campusCode = XlsxImportSupport.text(row.getCampusCode());
            this.termCode = XlsxImportSupport.text(row.getTermCode());
            this.courseCode = XlsxImportSupport.text(row.getCourseCode());
            this.classTypeCode = XlsxImportSupport.text(row.getClassTypeCode());
            this.teacherCode = XlsxImportSupport.text(row.getTeacherCode());
            this.capacityRaw = XlsxImportSupport.text(row.getCapacityLimit());
            this.weeklyLessonsRaw = XlsxImportSupport.text(row.getWeeklyLessons());
            this.openedAtText = XlsxImportSupport.text(row.getOpenedAt());
            this.status = XlsxImportSupport.text(row.getStatus());
            this.remark = XlsxImportSupport.text(row.getRemark());
            this.empty = classCode == null && className == null && campusCode == null
                && termCode == null && courseCode == null && classTypeCode == null
                && teacherCode == null && capacityRaw == null && weeklyLessonsRaw == null
                && openedAtText == null && status == null && remark == null;
        }

        private static Plan of(ClassImportRow row, int rowNum) {
            return new Plan(rowNum, row);
        }

        private XlsxImportRowView toView() {
            Map<String, String> cells = new LinkedHashMap<>();
            cells.put("classCode", classCode);
            cells.put("className", className);
            cells.put("campusCode", campusCode);
            cells.put("termCode", termCode);
            cells.put("courseCode", courseCode);
            cells.put("classTypeCode", classTypeCode);
            cells.put("teacherCode", teacherCode);
            cells.put("capacityLimit", capacityRaw);
            cells.put("weeklyLessons", weeklyLessonsRaw);
            cells.put("openedAt", openedAtText);
            cells.put("status", status);
            cells.put("remark", remark);
            return new XlsxImportRowView(rowNum, errors.isEmpty(),
                errors.isEmpty() ? null : String.join("；", errors),
                notices.isEmpty() ? null : String.join("；", notices), cells);
        }
    }
}
