package com.cornerstone.module.education.teachingclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.dto.XlsxImportRowView;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.education.support.XlsxImportSupport;
import com.cornerstone.module.education.support.XlsxImportTemplate;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentImportRow;
import com.cornerstone.module.education.teachingclass.service.ClassStudentImportService;
import com.cornerstone.module.education.teachingclass.service.ClassStudentService;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分班（班级学员关系）批量导入。
 *
 * <ol>
 *   <li><b>机构只认登录态</b>；班级按编码解析、学员按学号解析，两者都只能命中本机构的数据。</li>
 *   <li><b>班级编码跨校区可重码</b>（V105 守卫键是「校区ID + 编码」），所以模板留了可选的校区编码列：
 *       重码时填它消歧，报错里把候选校区列出来。</li>
 *   <li><b>已在班 = 跳过而不是报错</b>：重传整份文件不会整批失败，这是幂等导入的前提。</li>
 *   <li><b>容量预检</b>按「现有在班人数 + 本文件已接受的行数」投影，超出班级容量上限的行报错；
 *       真正落库仍走 {@code createClassStudent}，它自己会重算 current_students 并兜住并发。</li>
 * </ol>
 */
@Service
public class ClassStudentImportServiceImpl implements ClassStudentImportService {

    private static final int REMARK_MAX = 500;
    /** 字典条目的启用值 */
    private static final String ACTIVE = "active";
    /** 名册状态里"占容量"的那一档，与 createClassStudent 的容量统计口径一致 */
    private static final String ROSTER_ACTIVE = "active";

    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentMapper studentMapper;
    private final CampusMapper campusMapper;
    private final ClassStudentService classStudentService;
    private final DictValueValidator dictValueValidator;
    private final SystemDictService systemDictService;

    public ClassStudentImportServiceImpl(TeachingClassMapper teachingClassMapper,
                                         ClassStudentMapper classStudentMapper,
                                         StudentMapper studentMapper, CampusMapper campusMapper,
                                         ClassStudentService classStudentService,
                                         DictValueValidator dictValueValidator,
                                         SystemDictService systemDictService) {
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.studentMapper = studentMapper;
        this.campusMapper = campusMapper;
        this.classStudentService = classStudentService;
        this.dictValueValidator = dictValueValidator;
        this.systemDictService = systemDictService;
    }

    @Override
    public byte[] buildTemplate() {
        return XlsxImportTemplate.classStudent(activeDictNames("enrollment_channel"));
    }

    @Override
    public XlsxImportResult preview(MultipartFile file) {
        return validate(parse(file), XlsxImportSupport.requireInstitution("分班名册"));
    }

    @Override
    @Transactional
    public XlsxImportResult commit(MultipartFile file) {
        Long institutionId = XlsxImportSupport.requireInstitution("分班名册");
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
        List<ClassStudentImportRow> rows = XlsxImportSupport.readRows(file, ClassStudentImportRow.class);
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
        Map<String, String> channelValues = dictValueValidator.valueByLabel("enrollment_channel");
        for (Plan plan : plans) {
            plan.channel = XlsxImportSupport.labelToValue(channelValues, plan.channel);
        }
        return plans;
    }

    // ------------------------------------------------------------------ 校验

    private XlsxImportResult validate(List<Plan> plans, Long institutionId) {
        Map<String, List<TeachingClassEntity>> classesByCode = loadClasses(institutionId, plans);
        Map<String, CampusEntity> campusByCode = loadCampuses(institutionId);
        Map<String, StudentEntity> studentsByNo = loadStudents(institutionId, plans);

        for (Plan plan : plans) {
            checkClass(plan, classesByCode, campusByCode);
            checkStudent(plan, studentsByNo);
        }

        Map<String, String> existingRoster = loadExistingRoster(plans);
        Map<Long, Integer> projectedActive = initialActiveCounts(plans, existingRoster);
        Set<String> seenPairs = new LinkedHashSet<>();

        for (Plan plan : plans) {
            checkStudentName(plan);
            checkChannel(plan);
            checkJoinedAt(plan);
            checkRemark(plan);
            checkDuplicateInFile(plan, seenPairs);
            checkExisting(plan, existingRoster);
            checkCapacity(plan, projectedActive);
            plan.view = plan.toView();
        }
        return XlsxImportResult.preview(plans.stream().map(plan -> plan.view).toList());
    }

    /**
     * 只按本文件出现过的关键数据回查，不把全机构班级/学员拉回本机。
     * 受校区范围限制的账号看不到别的校区，编码也不出现在提示里。
     */
    private Map<String, List<TeachingClassEntity>> loadClasses(Long institutionId, List<Plan> plans) {
        Set<String> codes = new LinkedHashSet<>();
        for (Plan plan : plans) {
            if (plan.classCode != null) {
                codes.add(plan.classCode);
            }
        }
        Map<String, List<TeachingClassEntity>> classesByCode = new LinkedHashMap<>();
        if (codes.isEmpty()) {
            return classesByCode;
        }
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getInstitutionId, institutionId)
            .in(TeachingClassEntity::getClassCode, codes);
        CampusScopeGuard.apply(wrapper, TeachingClassEntity::getCampusId);
        for (TeachingClassEntity teachingClass : teachingClassMapper.selectList(wrapper)) {
            classesByCode.computeIfAbsent(teachingClass.getClassCode(), key -> new ArrayList<>())
                .add(teachingClass);
        }
        return classesByCode;
    }

    private Map<String, CampusEntity> loadCampuses(Long institutionId) {
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId);
        CampusScopeGuard.apply(wrapper, CampusEntity::getId);
        return campusMapper.selectList(wrapper).stream()
            .collect(Collectors.toMap(CampusEntity::getCampusCode, campus -> campus,
                (first, second) -> first, LinkedHashMap::new));
    }

    private Map<String, StudentEntity> loadStudents(Long institutionId, List<Plan> plans) {
        Set<String> nos = new LinkedHashSet<>();
        for (Plan plan : plans) {
            if (plan.studentNo != null) {
                nos.add(plan.studentNo);
            }
        }
        if (nos.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getInstitutionId, institutionId)
                .in(StudentEntity::getStudentNo, nos))
            .stream()
            .collect(Collectors.toMap(StudentEntity::getStudentNo, student -> student,
                (first, second) -> first));
    }

    /** 一次取回本文件涉及的班级的全部名册行：既用于"已在班跳过"，也用于容量基数 */
    private Map<String, String> loadExistingRoster(List<Plan> plans) {
        Set<Long> classIds = new LinkedHashSet<>();
        for (Plan plan : plans) {
            if (plan.teachingClass != null) {
                classIds.add(plan.teachingClass.getId());
            }
        }
        Map<String, String> statusByPair = new LinkedHashMap<>();
        if (classIds.isEmpty()) {
            return statusByPair;
        }
        classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudentEntity>()
                .in(ClassStudentEntity::getClassId, classIds))
            .forEach(relation -> statusByPair.putIfAbsent(
                pairKey(relation.getClassId(), relation.getStudentId()), relation.getStatus()));
        return statusByPair;
    }

    private Map<Long, Integer> initialActiveCounts(List<Plan> plans, Map<String, String> existingRoster) {
        Map<Long, Integer> counts = new HashMap<>();
        existingRoster.forEach((pair, status) -> {
            if (!ROSTER_ACTIVE.equals(status)) {
                return;
            }
            Long classId = Long.valueOf(pair.substring(0, pair.indexOf('#')));
            counts.merge(classId, 1, Integer::sum);
        });
        return counts;
    }

    private void checkClass(Plan plan, Map<String, List<TeachingClassEntity>> classesByCode,
                            Map<String, CampusEntity> campusByCode) {
        if (plan.classCode == null) {
            plan.errors.add("班级编码不能为空");
            return;
        }
        List<TeachingClassEntity> candidates = classesByCode.get(plan.classCode);
        if (candidates == null || candidates.isEmpty()) {
            plan.errors.add("未找到班级编码 " + plan.classCode + "，请到班级档案页核对");
            return;
        }
        CampusEntity campus = null;
        if (plan.campusCode != null) {
            campus = campusByCode.get(plan.campusCode);
            if (campus == null) {
                plan.errors.add("未找到校区编码 " + plan.campusCode
                    + "；本机构可用编码：" + String.join("、", campusByCode.keySet()));
                return;
            }
        }
        if (candidates.size() == 1) {
            TeachingClassEntity teachingClass = candidates.get(0);
            if (campus != null && !Objects.equals(teachingClass.getCampusId(), campus.getId())) {
                plan.errors.add("班级编码 " + plan.classCode + " 属于校区 "
                    + campusCodeOf(campusByCode, teachingClass.getCampusId()) + "，与填写的校区编码 "
                    + plan.campusCode + " 不一致");
                return;
            }
            plan.teachingClass = teachingClass;
            return;
        }
        // 同一编码在多个校区都存在：必须由校区编码消歧，不允许猜一个
        if (campus == null) {
            plan.errors.add("班级编码 " + plan.classCode + " 在本机构多个校区都存在，请填写校区编码区分："
                + candidates.stream()
                    .map(item -> campusCodeOf(campusByCode, item.getCampusId()))
                    .collect(Collectors.joining("、")));
            return;
        }
        Long campusId = campus.getId();
        plan.teachingClass = candidates.stream()
            .filter(item -> Objects.equals(item.getCampusId(), campusId))
            .findFirst()
            .orElse(null);
        if (plan.teachingClass == null) {
            plan.errors.add("校区编码 " + plan.campusCode + " 下没有班级编码 " + plan.classCode
                + "，请核对班级所属校区");
        }
    }

    private String campusCodeOf(Map<String, CampusEntity> campusByCode, Long campusId) {
        return campusByCode.values().stream()
            .filter(campus -> Objects.equals(campus.getId(), campusId))
            .map(CampusEntity::getCampusCode)
            .findFirst()
            .orElse(String.valueOf(campusId));
    }

    private void checkStudent(Plan plan, Map<String, StudentEntity> studentsByNo) {
        if (plan.studentNo == null) {
            plan.errors.add("学号不能为空");
            return;
        }
        StudentEntity student = studentsByNo.get(plan.studentNo);
        if (student == null) {
            plan.errors.add("未找到学号 " + plan.studentNo + "，请先在学员管理页导入/新增学员档案");
            return;
        }
        plan.student = student;
    }

    /** 姓名只做核对：填了就必须和学号对应档案一致，防止 Excel 串行把 A 塞进 B 的班 */
    private void checkStudentName(Plan plan) {
        if (plan.studentName == null || plan.student == null) {
            return;
        }
        if (!plan.studentName.equals(plan.student.getStudentName())) {
            plan.errors.add("学号 " + plan.studentNo + " 对应学员姓名为「"
                + plan.student.getStudentName() + "」，与填写的「" + plan.studentName + "」不一致");
        }
    }

    private void checkChannel(Plan plan) {
        try {
            dictValueValidator.validate("enrollment_channel", "来源渠道", plan.channel);
        } catch (BusinessException exception) {
            plan.errors.add(exception.getMessage());
        }
    }

    private void checkJoinedAt(Plan plan) {
        plan.joinedAt = XlsxImportSupport.parseDate(plan.joinedAtText, "入班日期", plan.errors);
    }

    private void checkRemark(Plan plan) {
        if (plan.remark != null && plan.remark.length() > REMARK_MAX) {
            plan.errors.add("备注长度不能超过 " + REMARK_MAX + " 个字符");
        }
    }

    private void checkDuplicateInFile(Plan plan, Set<String> seenPairs) {
        if (plan.teachingClass == null || plan.student == null) {
            return;
        }
        String key = pairKey(plan.teachingClass.getId(), plan.student.getId());
        if (!seenPairs.add(key)) {
            plan.errors.add("同一班级同一学号在本次文件里出现多行");
        }
    }

    private void checkExisting(Plan plan, Map<String, String> existingRoster) {
        if (plan.teachingClass == null || plan.student == null || !plan.errors.isEmpty()) {
            return;
        }
        String status = existingRoster.get(pairKey(plan.teachingClass.getId(), plan.student.getId()));
        if (status != null) {
            // 已在班是幂等导入的正常结果，不是错误；重传整份文件才不会整批失败
            plan.existing = true;
            plan.notices.add("该学员已在本班（当前状态：" + status + "），本次跳过；如需恢复请在名册页处理");
        }
    }

    private void checkCapacity(Plan plan, Map<Long, Integer> projectedActive) {
        if (plan.teachingClass == null || plan.student == null || plan.existing || !plan.errors.isEmpty()) {
            return;
        }
        int capacity = plan.teachingClass.getCapacityLimit() == null
            ? 0 : plan.teachingClass.getCapacityLimit();
        if (capacity <= 0) {
            return;
        }
        Long classId = plan.teachingClass.getId();
        int occupied = projectedActive.getOrDefault(classId, 0);
        if (occupied >= capacity) {
            plan.errors.add("班级「" + plan.teachingClass.getClassName() + "」容量已满（上限 "
                + capacity + "，当前在班 " + occupied + "）");
            return;
        }
        projectedActive.put(classId, occupied + 1);
    }

    // ------------------------------------------------------------------ 落库

    private XlsxImportResult persist(Long institutionId, List<Plan> plans) {
        int created = 0;
        int skipped = 0;
        for (Plan plan : plans) {
            if (plan.existing) {
                skipped++;
                continue;
            }
            classStudentService.createClassStudent(new ClassStudentCreateRequest(
                institutionId, plan.teachingClass.getId(), plan.student.getId(), null,
                plan.channel, plan.joinedAt, null, plan.remark));
            created++;
        }
        return XlsxImportResult.committed(created, skipped,
            plans.stream().map(plan -> plan.view).toList());
    }

    private List<String> activeDictNames(String dictType) {
        return systemDictService.listItems(dictType).stream()
            .filter(item -> ACTIVE.equals(item.status()))
            .map(DictItemView::itemName)
            .toList();
    }

    private static String pairKey(Long classId, Long studentId) {
        return classId + "#" + studentId;
    }

    /** 一行 Excel 在校验期间的全部中间结论 */
    private static final class Plan {

        private final int rowNum;
        private String classCode;
        private String campusCode;
        private String studentNo;
        private String studentName;
        private String channel;
        private String joinedAtText;
        private String remark;
        private final boolean empty;
        private final List<String> errors = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();
        private TeachingClassEntity teachingClass;
        private StudentEntity student;
        private LocalDate joinedAt;
        private boolean existing;
        private XlsxImportRowView view;

        private Plan(int rowNum, ClassStudentImportRow row) {
            this.rowNum = rowNum;
            this.classCode = XlsxImportSupport.text(row.getClassCode());
            this.campusCode = XlsxImportSupport.text(row.getCampusCode());
            this.studentNo = XlsxImportSupport.text(row.getStudentNo());
            this.studentName = XlsxImportSupport.text(row.getStudentName());
            this.channel = XlsxImportSupport.text(row.getChannel());
            this.joinedAtText = XlsxImportSupport.text(row.getJoinedAt());
            this.remark = XlsxImportSupport.text(row.getRemark());
            this.empty = classCode == null && campusCode == null && studentNo == null
                && studentName == null && channel == null && joinedAtText == null && remark == null;
        }

        private static Plan of(ClassStudentImportRow row, int rowNum) {
            return new Plan(rowNum, row);
        }

        private XlsxImportRowView toView() {
            Map<String, String> cells = new LinkedHashMap<>();
            cells.put("classCode", classCode);
            cells.put("campusCode", campusCode);
            cells.put("studentNo", studentNo);
            cells.put("studentName", studentName);
            cells.put("channel", channel);
            cells.put("joinedAt", joinedAtText);
            cells.put("remark", remark);
            return new XlsxImportRowView(rowNum, errors.isEmpty(),
                errors.isEmpty() ? null : String.join("；", errors),
                notices.isEmpty() ? null : String.join("；", notices), cells);
        }
    }
}
