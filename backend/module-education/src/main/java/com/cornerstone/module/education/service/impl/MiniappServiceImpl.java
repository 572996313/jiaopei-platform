package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.module.education.domain.entity.UserStudentRelEntity;
import com.cornerstone.module.education.dto.BindStudentRequest;
import com.cornerstone.module.education.dto.MiniappLessonView;
import com.cornerstone.module.education.dto.StudentBriefView;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.service.MiniappService;
import com.cornerstone.module.education.dto.StudentBindSelfRequest;
import com.cornerstone.module.education.dto.StudentOwnProfileView;
import com.cornerstone.module.education.dto.StudentProfileSaveRequest;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.BaseEntity;
import com.cornerstone.core.system.service.SystemConfigService;import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniappServiceImpl implements MiniappService {

    private static final String ACTIVE = "active";
    /** jp_class_student 中"在读"状态值(见 ClassStudentEntity 注释) */
    private static final String CLASS_STUDENT_ACTIVE = "ACTIVE";

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

    public MiniappServiceImpl(UserStudentRelMapper userStudentRelMapper, StudentMapper studentMapper,
                              StudentParentMapper studentParentMapper, ClassStudentMapper classStudentMapper,
                              TeachingClassMapper teachingClassMapper, ScheduleLessonMapper scheduleLessonMapper,
                              CourseMapper courseMapper, ClassroomMapper classroomMapper, TeacherMapper teacherMapper,
                              SystemConfigService systemConfigService) {
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
        Set<Long> courseIdSet = classes.values().stream().map(TeachingClassEntity::getCourseId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CourseEntity> courses = courseIdSet.isEmpty() ? Map.of()
            : groupById(courseMapper.selectBatchIds(new ArrayList<>(courseIdSet)));
        Set<Long> classroomIdSet = lessons.stream().map(ScheduleLessonEntity::getClassroomId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ClassroomEntity> classrooms = classroomIdSet.isEmpty() ? Map.of()
            : groupById(classroomMapper.selectBatchIds(new ArrayList<>(classroomIdSet)));
        Set<Long> teacherIdSet = classes.values().stream().map(TeachingClassEntity::getTeacherId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, TeacherEntity> teachers = teacherIdSet.isEmpty() ? Map.of()
            : groupById(teacherMapper.selectBatchIds(new ArrayList<>(teacherIdSet)));

        return lessons.stream().map(lesson -> {
            TeachingClassEntity clazz = classes.get(lesson.getClassId());
            CourseEntity course = clazz == null ? null : courses.get(clazz.getCourseId());
            ClassroomEntity classroom = classrooms.get(lesson.getClassroomId());
            TeacherEntity teacher = clazz == null ? null : teachers.get(clazz.getTeacherId());
            return new MiniappLessonView(
                lesson.getId(),
                lesson.getLessonDate(),
                lesson.getLessonWeekday(),
                lesson.getStartTime(),
                lesson.getEndTime(),
                lesson.getLessonTitle(),
                lesson.getLessonStatus(),
                clazz == null ? null : clazz.getClassName(),
                course == null ? null : course.getSubjectName() != null ? course.getSubjectName() : course.getCourseName(),
                classroom == null ? null : classroom.getClassroomName(),
                teacher == null ? null : teacher.getTeacherName(),
                teacher == null ? null : teacher.getPhone()
            );
        }).toList();
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
            created.setStudentNo(generateStudentNo());
            created.setStudentName(request.studentName().trim());
            created.setGender(request.gender());
            created.setBirthday(request.birthday());
            created.setStatus("studying");
            studentMapper.insert(created);
            if (created.getId() != null) {
                insertSelfRelation(userId, created.getId(), created.getInstitutionId());
            }
            return toOwnProfile(created);
        }
        // 已归属:仅可编辑姓名/性别/生日
        student.setStudentName(request.studentName().trim());
        student.setGender(request.gender());
        student.setBirthday(request.birthday());
        studentMapper.updateById(student);
        return toOwnProfile(student);
    }

    // ---- 内部辅助 ----

    /** 仅学生端用户(角色含 MINIAPP_STUDENT)可调用学生档案相关方法 */
    private void requireStudentPortal() {
        AuthContext.requireUser().roles().stream()
            .filter("MINIAPP_STUDENT"::equals)
            .findAny()
            .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "仅学生账号可操作自己的档案"));
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
            .filter(r -> r.getJoinedAt() == null || !r.getJoinedAt().isAfter(today))
            .filter(r -> r.getLeftAt() == null || !r.getLeftAt().isBefore(today))
            .map(ClassStudentEntity::getClassId)
            .distinct()
            .toList();
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