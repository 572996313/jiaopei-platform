package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.module.education.domain.entity.UserStudentRelEntity;
import com.cornerstone.module.education.dto.BindStudentRequest;
import com.cornerstone.module.education.dto.MiniappClassmateGroupView;
import com.cornerstone.module.education.dto.MiniappHomeLessonView;
import com.cornerstone.module.education.dto.MiniappHomeOverviewView;
import com.cornerstone.module.education.dto.MiniappLessonView;
import com.cornerstone.module.education.dto.StudentBriefView;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.service.MiniappService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.education.dto.StudentBindSelfRequest;
import com.cornerstone.module.education.dto.StudentOwnProfileView;
import com.cornerstone.module.education.dto.StudentProfileSaveRequest;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.BaseEntity;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.StudentParentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.StudentParentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniappServiceImpl implements MiniappService {

    private static final String ACTIVE = "active";
    /** jp_class_student 的在班状态值(见 ClassStudentEntity 注释)，与其余按 status='active' 过滤任课/在班的查询同口径 */
    private static final String CLASS_STUDENT_ACTIVE = ACTIVE;
    /** 家长账号显示名的后缀：孩子姓名顿号连接 + 该后缀 */
    private static final String PARENT_NAME_SUFFIX = "家长";
    /** 停课课次不进首页今日列表与本周节数:那是"不用去"的课,算进去会把家长引到空教室 */
    private static final String LESSON_STATUS_CANCELLED = "cancelled";
    /** 同学名单封顶:正常班级几十人,超出即数据异常,页面提示联系机构而不是整页渲染 */
    private static final int CLASSMATE_LIMIT = 200;

    private final UserStudentRelMapper userStudentRelMapper;
    private final StudentMapper studentMapper;
    private final StudentParentMapper studentParentMapper;
    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final CourseMapper courseMapper;
    private final ClassroomMapper classroomMapper;
    private final TeacherMapper teacherMapper;
    private final SystemConfigService systemConfigService;
    private final DictValueValidator dictValueValidator;
    private final AuthApplicationService authApplicationService;
    private final CodeRuleService codeRuleService;

    public MiniappServiceImpl(UserStudentRelMapper userStudentRelMapper, StudentMapper studentMapper,
                              StudentParentMapper studentParentMapper, ClassStudentMapper classStudentMapper,
                              TeachingClassMapper teachingClassMapper, ScheduleLessonMapper scheduleLessonMapper,
                              CourseMapper courseMapper, ClassroomMapper classroomMapper, TeacherMapper teacherMapper,
                              SystemConfigService systemConfigService, DictValueValidator dictValueValidator,
                              AuthApplicationService authApplicationService,
                              CodeRuleService codeRuleService) {
        this.userStudentRelMapper = userStudentRelMapper;
        this.studentMapper = studentMapper;
        this.studentParentMapper = studentParentMapper;
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.scheduleLessonMapper = scheduleLessonMapper;
        this.courseMapper = courseMapper;
        this.classroomMapper = classroomMapper;
        this.teacherMapper = teacherMapper;
        this.systemConfigService = systemConfigService;
        this.dictValueValidator = dictValueValidator;
        this.authApplicationService = authApplicationService;
        this.codeRuleService = codeRuleService;
    }

    @Override
    @Transactional
    public StudentBriefView bindStudent(BindStudentRequest request) {
        Long userId = AuthContext.requireUser().userId();
        StudentEntity student = resolveStudent(request);
        Long existingCount = userStudentRelMapper.selectCount(new LambdaQueryWrapper<UserStudentRelEntity>()
            .eq(UserStudentRelEntity::getUserId, userId)
            .eq(UserStudentRelEntity::getStudentId, student.getId()));
        if (existingCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员已绑定，请勿重复绑定");
        }
        UserStudentRelEntity relation = new UserStudentRelEntity();
        relation.setInstitutionId(student.getInstitutionId());
        relation.setUserId(userId);
        relation.setStudentId(student.getId());
        relation.setRemark(request.remark());
        userStudentRelMapper.insert(relation);
        syncAccountDisplayName();
        return toStudentBrief(student);
    }

    @Override
    @Transactional
    public void unbindStudent(Long studentId) {
        Long userId = AuthContext.requireUser().userId();
        int deleted = userStudentRelMapper.physicalDelete(userId, studentId);
        if (deleted == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未找到该学员的绑定关系,请勿重复解绑");
        }
        syncAccountDisplayName();
    }

    @Override
    public List<StudentBriefView> listMyStudents() {
        Long userId = AuthContext.requireUser().userId();
        List<UserStudentRelEntity> relations = userStudentRelMapper.selectList(new LambdaQueryWrapper<UserStudentRelEntity>()
            .eq(UserStudentRelEntity::getUserId, userId)
            .orderByDesc(UserStudentRelEntity::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = relations.stream().map(UserStudentRelEntity::getStudentId).toList();
        Map<Long, StudentEntity> students = groupById(studentMapper.selectBatchIds(studentIds));
        // 备注从关联行带出
        Map<Long, String> remarkByStudent = relations.stream()
            .collect(Collectors.toMap(UserStudentRelEntity::getStudentId, r -> Objects.toString(r.getRemark(), ""), (a, b) -> a));
        return students.values().stream()
            .filter(Objects::nonNull)
            .map(s -> toStudentBrief(s, remarkByStudent.get(s.getId())))
            .collect(Collectors.toList());
    }

    @Override
    public List<MiniappLessonView> studentLessons(Long studentId, LocalDate start, LocalDate end) {
        Long userId = AuthContext.requireUser().userId();
        // 数据隔离:必须当前用户已关联该学员,否则视为不可访问
        ensureBound(userId, studentId);

        List<Long> classIds = currentClassIds(studentId);
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<ScheduleLessonEntity> lessons = scheduleLessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
            .in(ScheduleLessonEntity::getClassId, classIds)
            .ge(ScheduleLessonEntity::getLessonDate, start)
            .le(ScheduleLessonEntity::getLessonDate, end)
            .orderByAsc(ScheduleLessonEntity::getLessonDate, ScheduleLessonEntity::getStartTime));
        if (lessons.isEmpty()) {
            return List.of();
        }

        Map<Long, TeachingClassEntity> classes = groupById(teachingClassMapper.selectBatchIds(classIds));
        Map<Long, CourseEntity> courses = coursesOfClasses(classes.values());
        Map<Long, ClassroomEntity> classrooms = classroomsOfLessons(lessons);
        Map<Long, TeacherEntity> teachers = teachersOfClasses(classes.values());

        return lessons.stream().map(lesson -> {
            TeachingClassEntity clazz = classes.get(lesson.getClassId());
            ClassroomEntity classroom = classrooms.get(lesson.getClassroomId());
            return new MiniappLessonView(
                lesson.getId(),
                lesson.getLessonDate(),
                lesson.getLessonWeekday(),
                lesson.getStartTime(),
                lesson.getEndTime(),
                lesson.getLessonTitle(),
                lesson.getLessonStatus(),
                clazz == null ? null : clazz.getClassName(),
                courseNameOf(clazz, courses),
                classroom == null ? null : classroom.getClassroomName(),
                teacherSalutation(clazz == null ? null : teachers.get(clazz.getTeacherId()))
            );
        }).toList();
    }

    @Override
    public List<MiniappClassmateGroupView> studentClassmates(Long studentId) {
        Long userId = AuthContext.requireUser().userId();
        // 数据隔离:必须当前用户已关联该学员,否则视为不可访问
        ensureBound(userId, studentId);
        List<Long> classIds = currentClassIds(studentId);
        if (classIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TeachingClassEntity> classes = groupById(teachingClassMapper.selectBatchIds(classIds));
        LocalDate today = LocalDate.now();
        Map<Long, List<ClassStudentEntity>> membersByClass = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudentEntity>()
                    .in(ClassStudentEntity::getClassId, classIds)
                    .eq(ClassStudentEntity::getStatus, CLASS_STUDENT_ACTIVE))
            .stream()
            .filter(relation -> isInClassNow(relation, today))
            .collect(Collectors.groupingBy(ClassStudentEntity::getClassId));

        List<MiniappClassmateGroupView> groups = new ArrayList<>();
        for (Long classId : classIds) {
            List<ClassStudentEntity> members = membersByClass.getOrDefault(classId, List.of());
            List<Long> classmateIds = members.stream()
                .map(ClassStudentEntity::getStudentId)
                .filter(id -> !id.equals(studentId))
                .distinct()
                .toList();
            TeachingClassEntity clazz = classes.get(classId);
            if (classmateIds.isEmpty()) {
                groups.add(new MiniappClassmateGroupView(classId, clazz == null ? null : clazz.getClassName(),
                    members.size(), List.of()));
                continue;
            }
            // 按学号排,名单顺序稳定;学号只用于排序,不出现在返回值里
            List<String> names = studentMapper.selectBatchIds(classmateIds).stream()
                .sorted(Comparator.comparing(StudentEntity::getStudentNo,
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(CLASSMATE_LIMIT)
                .map(StudentEntity::getStudentName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
            groups.add(new MiniappClassmateGroupView(classId, clazz == null ? null : clazz.getClassName(),
                members.size(), names));
        }
        return groups;
    }

    @Override
    public MiniappHomeOverviewView homeOverview() {
        Long userId = AuthContext.requireUser().userId();
        List<UserStudentRelEntity> relations = userStudentRelMapper.selectList(new LambdaQueryWrapper<UserStudentRelEntity>()
            .eq(UserStudentRelEntity::getUserId, userId)
            .orderByAsc(UserStudentRelEntity::getId));
        if (relations.isEmpty()) {
            return new MiniappHomeOverviewView(Boolean.FALSE, List.of(), 0, null, List.of());
        }
        List<Long> studentIds = relations.stream().map(UserStudentRelEntity::getStudentId).toList();
        Map<Long, StudentEntity> students = groupById(studentMapper.selectBatchIds(studentIds));

        LocalDate today = LocalDate.now();
        List<ClassStudentEntity> inClass = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudentEntity>()
                .in(ClassStudentEntity::getStudentId, studentIds)
                .eq(ClassStudentEntity::getStatus, CLASS_STUDENT_ACTIVE))
            .stream()
            .filter(relation -> isInClassNow(relation, today))
            .toList();
        List<String> studentsWithoutClass = students.values().stream()
            .filter(student -> inClass.stream().noneMatch(r -> r.getStudentId().equals(student.getId())))
            .map(StudentEntity::getStudentName)
            .filter(name -> name != null && !name.isBlank())
            .toList();
        List<Long> classIds = inClass.stream().map(ClassStudentEntity::getClassId).distinct().toList();
        if (classIds.isEmpty()) {
            return new MiniappHomeOverviewView(Boolean.TRUE, List.of(), 0, null, studentsWithoutClass);
        }

        Map<Long, TeachingClassEntity> classes = groupById(teachingClassMapper.selectBatchIds(classIds));
        // 同一班可能有该账号的两个孩子,所以班级到学员是列表;拼成"小明、小红"一个角标显示
        Map<Long, List<StudentEntity>> studentsByClass = new LinkedHashMap<>();
        for (ClassStudentEntity relation : inClass) {
            StudentEntity student = students.get(relation.getStudentId());
            if (student != null) {
                studentsByClass.computeIfAbsent(relation.getClassId(), key -> new ArrayList<>()).add(student);
            }
        }

        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        List<ScheduleLessonEntity> lessons = scheduleLessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
                .in(ScheduleLessonEntity::getClassId, classIds)
                .ge(ScheduleLessonEntity::getLessonDate, monday)
                .le(ScheduleLessonEntity::getLessonDate, monday.plusDays(6))
                .ne(ScheduleLessonEntity::getLessonStatus, LESSON_STATUS_CANCELLED)
                .orderByAsc(ScheduleLessonEntity::getLessonDate, ScheduleLessonEntity::getStartTime));
        Map<Long, CourseEntity> courses = coursesOfClasses(classes.values());
        Map<Long, ClassroomEntity> classrooms = classroomsOfLessons(lessons);
        Map<Long, TeacherEntity> teachers = teachersOfClasses(classes.values());

        List<MiniappHomeLessonView> weekLessons = lessons.stream()
            .map(lesson -> toHomeLesson(lesson, classes, studentsByClass, courses, classrooms, teachers))
            .sorted(Comparator.comparing(MiniappHomeLessonView::lessonDate)
                .thenComparing(MiniappHomeLessonView::startTime))
            .toList();
        LocalTime now = LocalTime.now();
        List<MiniappHomeLessonView> todayLessons = weekLessons.stream()
            .filter(view -> today.equals(view.lessonDate()))
            .toList();
        MiniappHomeLessonView nextLesson = weekLessons.stream()
            .filter(view -> view.lessonDate().isAfter(today)
                || (today.equals(view.lessonDate()) && view.startTime().isAfter(now)))
            .findFirst()
            .orElse(null);
        return new MiniappHomeOverviewView(Boolean.TRUE, todayLessons, weekLessons.size(), nextLesson, studentsWithoutClass);
    }

    @Override
    public StudentOwnProfileView myStudentProfile() {
        StudentEntity student = myOwnedStudentOrNull();
        return student == null ? new StudentOwnProfileView(Boolean.FALSE, null, null, null, null, null, null)
            : toOwnProfile(student);
    }

    @Override
    @Transactional
    public StudentOwnProfileView bindSelfStudent(StudentBindSelfRequest request) {
        requireStudentPortal();
        StudentEntity student = studentMapper.selectOne(new LambdaQueryWrapper<StudentEntity>()
            .eq(StudentEntity::getStudentNo, request.studentNo().trim()));
        if (student == null || !request.studentName().trim().equals(student.getStudentName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员信息不匹配,请核对学号与姓名后重试");
        }
        Long userId = AuthContext.requireUser().userId();
        // 该档案已被别人归属:拒绝改绑(绑定即终身)
        if (student.getUserId() != null && !student.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该档案已归属其他学生账号,无法绑定");
        }
        // 已归属当前账号:幂等直接返回
        if (student.getUserId() != null) {
            return toOwnProfile(student);
        }
        student.setUserId(userId);
        studentMapper.updateById(student);
        // 顺带建关联,让学生复用现有课表查询(按 user-student rel 过滤)
        insertSelfRelation(userId, student.getId(), student.getInstitutionId());
        syncAccountDisplayName();
        return toOwnProfile(student);
    }

    @Override
    @Transactional
    public StudentOwnProfileView saveStudentProfile(StudentProfileSaveRequest request) {
        requireStudentPortal();
        Long userId = AuthContext.requireUser().userId();
        StudentEntity student = myOwnedStudentOrNull();
        if (student == null) {
            // 未归档:新建档案并归属自己,学号自动生成
            StudentEntity created = new StudentEntity();
            created.setInstitutionId(Long.valueOf(
                systemConfigService.getIntConfig("auth.register.default_institution_id", 1)));
            created.setUserId(userId);
            created.setStudentNo(nextStudentNo());
            created.setStudentName(request.studentName().trim());
            created.setGender(dictValueValidator.validate("gender", "性别", request.gender()));
            created.setBirthday(request.birthday());
            created.setStatus("studying");
            studentMapper.insert(created);
            if (created.getId() != null) {
                insertSelfRelation(userId, created.getId(), created.getInstitutionId());
            }
            syncAccountDisplayName();
            return toOwnProfile(created);
        }
        // 已归属:仅可编辑姓名/性别/生日
        student.setStudentName(request.studentName().trim());
        student.setGender(dictValueValidator.validate("gender", "性别", request.gender()));
        student.setBirthday(request.birthday());
        studentMapper.updateById(student);
        syncAccountDisplayName();
        return toOwnProfile(student);
    }

    // ---- 内部辅助 ----

    /** 仅学生端用户(角色含 MINIAPP_STUDENT)可调用学生档案相关方法 */
    private void requireStudentPortal() {
        if (!isStudentPortal()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅学生账号可操作自己的档案");
        }
    }

    private boolean isStudentPortal() {
        return AuthContext.requireUser().roles().contains(MiniappPortalResolver.ROLE_CODE_STUDENT);
    }

    /**
     * 档案绑定关系变化后，把账号显示名同步成档案给出的名字：学生用自己的档案姓名，家长用孩子姓名拼接。
     *
     * <p>名字来源一律取档案而不是注册时填的微信昵称，管理员与本人看账号列表时才能一眼知道这个账号是谁。
     * 无档案可依据时（家长解绑了全部孩子、学生还没归档）保留现有名字：显示名列非空，清空只能塞占位文案，
     * 而占位文案会把"上一个孩子的家长"直接盖成"微信用户"，比留着旧名字更难看。</p>
     */
    private void syncAccountDisplayName() {
        Long userId = AuthContext.requireUser().userId();
        if (isStudentPortal()) {
            StudentEntity own = myOwnedStudentOrNull();
            if (own != null) {
                authApplicationService.updateDisplayName(userId, own.getStudentName());
            }
            return;
        }
        List<String> childNames = boundStudentNames(userId);
        if (!childNames.isEmpty()) {
            authApplicationService.updateDisplayName(userId, composeParentDisplayName(childNames));
        }
    }

    /** 已绑定学员的姓名，按绑定先后排序，保证同一批绑定无论重算多少次都得到同一个名字 */
    private List<String> boundStudentNames(Long userId) {
        List<UserStudentRelEntity> relations = userStudentRelMapper.selectList(new LambdaQueryWrapper<UserStudentRelEntity>()
            .eq(UserStudentRelEntity::getUserId, userId)
            .orderByAsc(UserStudentRelEntity::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = relations.stream().map(UserStudentRelEntity::getStudentId).toList();
        Map<Long, StudentEntity> students = groupById(studentMapper.selectBatchIds(studentIds));
        return relations.stream()
            .map(relation -> students.get(relation.getStudentId()))
            .filter(Objects::nonNull)
            .map(StudentEntity::getStudentName)
            .filter(name -> name != null && !name.isBlank())
            .toList();
    }

    /**
     * 家长显示名 =「孩子A、孩子B家长」。列宽上限 128，孩子多或姓名长时按能放下的孩子数截断并补「等」，
     * 连一个孩子都放不下时截孩子姓名保住后缀——整句被裁掉会看不出这是谁的家长。
     */
    private String composeParentDisplayName(List<String> childNames) {
        String all = String.join("、", childNames) + PARENT_NAME_SUFFIX;
        if (all.length() <= AuthApplicationService.MAX_DISPLAY_NAME_LENGTH) {
            return all;
        }
        for (int keep = childNames.size() - 1; keep >= 1; keep--) {
            String candidate = String.join("、", childNames.subList(0, keep)) + "等" + PARENT_NAME_SUFFIX;
            if (candidate.length() <= AuthApplicationService.MAX_DISPLAY_NAME_LENGTH) {
                return candidate;
            }
        }
        int budget = AuthApplicationService.MAX_DISPLAY_NAME_LENGTH - PARENT_NAME_SUFFIX.length();
        return childNames.get(0).substring(0, budget) + PARENT_NAME_SUFFIX;
    }

    /** 当前账号归属的学生档案;未归属返回 null */
    private StudentEntity myOwnedStudentOrNull() {
        Long userId = AuthContext.requireUser().userId();
        return studentMapper.selectOne(new LambdaQueryWrapper<StudentEntity>()
            .eq(StudentEntity::getUserId, userId)
            .last("LIMIT 1"));
    }

    private StudentOwnProfileView toOwnProfile(StudentEntity student) {
        return new StudentOwnProfileView(
            Boolean.TRUE,
            student.getId(),
            student.getStudentNo(),
            student.getStudentName(),
            student.getGender(),
            student.getBirthday(),
            currentClassName(student.getId())
        );
    }

    /** 学生归属自己档案时,顺带建一条用户-学员关联,复用现有课表查询权限 */
    private void insertSelfRelation(Long userId, Long studentId, Long institutionId) {
        Long existing = userStudentRelMapper.selectCount(new LambdaQueryWrapper<UserStudentRelEntity>()
            .eq(UserStudentRelEntity::getUserId, userId)
            .eq(UserStudentRelEntity::getStudentId, studentId));
        if (existing != null && existing > 0) {
            return;
        }
        UserStudentRelEntity relation = new UserStudentRelEntity();
        relation.setInstitutionId(institutionId);
        relation.setUserId(userId);
        relation.setStudentId(studentId);
        userStudentRelMapper.insert(relation);
    }

    /**
     * 自助建档没有人工填号这一步：启用了编号规则就按规则取号，没启用沿用原来的时间戳号，
     * 保证这条路径永远不会因为"没配规则"而建不出档案。
     */
    private String nextStudentNo() {
        String fromRule = codeRuleService.autoCodeIfEnabled("student");
        return fromRule != null ? fromRule : generateStudentNo();
    }

    /** 学号自动生成:S + 13位时间戳(yyMMddHHmmss) + 3位随机,机构范围即可防重 */
    private String generateStudentNo() {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        int rand = (int) (Math.random() * 900) + 100;
        return "S" + ts + rand;
    }

    private StudentEntity resolveStudent(BindStudentRequest request) {
        String expectedName = request.studentName().trim();
        if ("PARENT_PHONE".equals(request.method())) {
            String phone = request.value().trim();
            if (phone.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "手机号不能为空");
            }
            // 先按手机号查家长关系,再按姓名过滤:一个手机号可能关联多个孩子,姓名能精确锁定目标
            List<StudentParentEntity> parents = studentParentMapper.selectList(new LambdaQueryWrapper<StudentParentEntity>()
                .eq(StudentParentEntity::getParentPhone, phone));
            if (parents.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "未找到该手机号对应学员,请核对手机号与学员姓名");
            }
            return parents.stream()
                .map(parent -> studentMapper.selectById(parent.getStudentId()))
                .filter(student -> student != null && expectedName.equals(student.getStudentName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST,
                    "学员信息不匹配,请核对姓名后重试"));
        }
        // 默认 STUDENT_NO
        String studentNo = request.value().trim();
        if (studentNo.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员编号不能为空");
        }
        StudentEntity student = studentMapper.selectOne(new LambdaQueryWrapper<StudentEntity>()
            .eq(StudentEntity::getStudentNo, studentNo));
        if (student == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未找到该学员编号对应学员,请核对编号与学员姓名");
        }
        if (!expectedName.equals(student.getStudentName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员信息不匹配,请核对姓名后重试");
        }
        return student;
    }

    private void ensureBound(Long userId, Long studentId) {
        Long count = userStudentRelMapper.selectCount(new LambdaQueryWrapper<UserStudentRelEntity>()
            .eq(UserStudentRelEntity::getUserId, userId)
            .eq(UserStudentRelEntity::getStudentId, studentId));
        if (count == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该学员的信息");
        }
    }

    /** 学员当前在班的班级ID列表(ACTIVE 且在班日期范围内) */
    private List<Long> currentClassIds(Long studentId) {
        List<ClassStudentEntity> relations = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getStudentId, studentId)
            .eq(ClassStudentEntity::getStatus, CLASS_STUDENT_ACTIVE));
        LocalDate today = LocalDate.now();
        return relations.stream()
            .filter(r -> isInClassNow(r, today))
            .map(ClassStudentEntity::getClassId)
            .distinct()
            .toList();
    }

    /** 在班日期窗口:joined_at 未到、left_at 已过都不算"现在在这个班" */
    private boolean isInClassNow(ClassStudentEntity relation, LocalDate today) {
        boolean joined = relation.getJoinedAt() == null || !relation.getJoinedAt().isAfter(today);
        boolean notLeft = relation.getLeftAt() == null || !relation.getLeftAt().isBefore(today);
        return joined && notLeft;
    }

    private Map<Long, CourseEntity> coursesOfClasses(Collection<TeachingClassEntity> classes) {
        List<Long> courseIds = classes.stream().map(TeachingClassEntity::getCourseId)
            .filter(Objects::nonNull).distinct().toList();
        return courseIds.isEmpty() ? Map.of() : groupById(courseMapper.selectBatchIds(courseIds));
    }

    private Map<Long, TeacherEntity> teachersOfClasses(Collection<TeachingClassEntity> classes) {
        List<Long> teacherIds = classes.stream().map(TeachingClassEntity::getTeacherId)
            .filter(Objects::nonNull).distinct().toList();
        return teacherIds.isEmpty() ? Map.of() : groupById(teacherMapper.selectBatchIds(teacherIds));
    }

    private Map<Long, ClassroomEntity> classroomsOfLessons(Collection<ScheduleLessonEntity> lessons) {
        List<Long> classroomIds = lessons.stream().map(ScheduleLessonEntity::getClassroomId)
            .filter(Objects::nonNull).distinct().toList();
        return classroomIds.isEmpty() ? Map.of() : groupById(classroomMapper.selectBatchIds(classroomIds));
    }

    /** 班级没有直接挂课程时课程名取不到,回落课次标题由调用方处理 */
    private String courseNameOf(TeachingClassEntity clazz, Map<Long, CourseEntity> courses) {
        CourseEntity course = clazz == null ? null : courses.get(clazz.getCourseId());
        if (course == null) {
            return null;
        }
        return course.getSubjectName() != null ? course.getSubjectName() : course.getCourseName();
    }

    /**
     * 教师对家长与学生的称呼:只给姓。全名、手机号等联系方式都不下发——课表是给几百个家庭看的,
     * 而教师档案里的电话是机构内部联系口径。复姓按首字显示(欧阳→欧老师),宁可少给一个字也不给全名。
     */
    private String teacherSalutation(TeacherEntity teacher) {
        String name = teacher == null ? null : teacher.getTeacherName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().substring(0, 1) + "老师";
    }

    private MiniappHomeLessonView toHomeLesson(ScheduleLessonEntity lesson, Map<Long, TeachingClassEntity> classes,
                                               Map<Long, List<StudentEntity>> studentsByClass,
                                               Map<Long, CourseEntity> courses, Map<Long, ClassroomEntity> classrooms,
                                               Map<Long, TeacherEntity> teachers) {
        TeachingClassEntity clazz = classes.get(lesson.getClassId());
        ClassroomEntity classroom = classrooms.get(lesson.getClassroomId());
        String studentNames = studentsByClass.getOrDefault(lesson.getClassId(), List.of()).stream()
            .map(StudentEntity::getStudentName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .collect(Collectors.joining("、"));
        return new MiniappHomeLessonView(
            studentNames,
            lesson.getLessonDate(),
            lesson.getStartTime(),
            lesson.getEndTime(),
            courseNameOf(clazz, courses),
            clazz == null ? null : clazz.getClassName(),
            classroom == null ? null : classroom.getClassroomName(),
            teacherSalutation(clazz == null ? null : teachers.get(clazz.getTeacherId())),
            lesson.getLessonStatus()
        );
    }

    private StudentBriefView toStudentBrief(StudentEntity student) {
        return toStudentBrief(student, null);
    }

    private StudentBriefView toStudentBrief(StudentEntity student, String remark) {
        return new StudentBriefView(
            student.getId(),
            student.getStudentNo(),
            student.getStudentName(),
            student.getGender(),
            currentClassName(student.getId()),
            remark
        );
    }

    private String currentClassName(Long studentId) {
        List<Long> classIds = currentClassIds(studentId);
        if (classIds.isEmpty()) {
            return null;
        }
        TeachingClassEntity clazz = teachingClassMapper.selectById(classIds.get(0));
        return clazz == null ? null : clazz.getClassName();
    }

    private <T extends BaseEntity> Map<Long, T> groupById(List<T> entities) {
        return entities.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity(), (a, b) -> a));
    }
}