package com.jiaopei.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.context.AuthContext;
import com.jiaopei.platform.auth.domain.UserStudentRelEntity;
import com.jiaopei.platform.auth.dto.BindStudentRequest;
import com.jiaopei.platform.auth.dto.MiniappLessonView;
import com.jiaopei.platform.auth.dto.StudentBriefView;
import com.jiaopei.platform.auth.mapper.UserStudentRelMapper;
import com.jiaopei.platform.auth.service.MiniappService;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.common.model.BaseEntity;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.domain.entity.CourseEntity;
import com.jiaopei.platform.education.domain.entity.ScheduleLessonEntity;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.StudentParentEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.mapper.ClassStudentMapper;
import com.jiaopei.platform.education.mapper.CourseMapper;
import com.jiaopei.platform.education.mapper.ScheduleLessonMapper;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.StudentParentMapper;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.mapper.ClassroomMapper;
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

    public MiniappServiceImpl(UserStudentRelMapper userStudentRelMapper, StudentMapper studentMapper,
                              StudentParentMapper studentParentMapper, ClassStudentMapper classStudentMapper,
                              TeachingClassMapper teachingClassMapper, ScheduleLessonMapper scheduleLessonMapper,
                              CourseMapper courseMapper, ClassroomMapper classroomMapper, TeacherMapper teacherMapper) {
        this.userStudentRelMapper = userStudentRelMapper;
        this.studentMapper = studentMapper;
        this.studentParentMapper = studentParentMapper;
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.scheduleLessonMapper = scheduleLessonMapper;
        this.courseMapper = courseMapper;
        this.classroomMapper = classroomMapper;
        this.teacherMapper = teacherMapper;
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

    // ---- 内部辅助 ----

    private StudentEntity resolveStudent(BindStudentRequest request) {
        if ("PARENT_PHONE".equals(request.method())) {
            String phone = request.value().trim();
            if (phone.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "手机号不能为空");
            }
            // 通过家长关系表按手机号找学员;可能存在多个孩子同手机号,取其一提示
            List<StudentParentEntity> parents = studentParentMapper.selectList(new LambdaQueryWrapper<StudentParentEntity>()
                .eq(StudentParentEntity::getParentPhone, phone));
            if (parents.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "未找到该手机号对应的学员档案，请核对或选择学员编号绑定");
            }
            Long studentId = parents.get(0).getStudentId();
            StudentEntity student = studentMapper.selectById(studentId);
            if (student == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "学员档案不存在");
            }
            return student;
        }
        // 默认 STUDENT_NO
        String studentNo = request.value().trim();
        if (studentNo.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员编号不能为空");
        }
        StudentEntity student = studentMapper.selectOne(new LambdaQueryWrapper<StudentEntity>()
            .eq(StudentEntity::getStudentNo, studentNo));
        if (student == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未找到该学员编号对应的学员档案");
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