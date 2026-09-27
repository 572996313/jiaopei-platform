package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ClassOptionView;
import com.cornerstone.module.education.dto.TeachingClassCreateRequest;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ClassTypeMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ClassChangeNoticeService;
import com.cornerstone.module.education.service.ClassTeacherService;
import com.cornerstone.module.education.service.TeachingClassService;
import com.cornerstone.module.education.service.support.LessonTargetConverter;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachingClassServiceImpl implements TeachingClassService {

    /** 下拉一次最多给多少个班：够看够选就行，再多就是白画 DOM */
    private static final int CLASS_OPTION_LIMIT = 30;

    /** 按 id 批量回查名称时的上限：一屏课次/计划涉及的班级数量级，超出的按未找到处理 */
    private static final int CLASS_LABEL_LIMIT = 200;

    private final TeachingClassMapper teachingClassMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;
    private final TermMapper termMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final ClassTypeMapper classTypeMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassChangeNoticeService classChangeNoticeService;
    private final ClassTeacherService classTeacherService;

    public TeachingClassServiceImpl(TeachingClassMapper teachingClassMapper, InstitutionMapper institutionMapper,
                                    CampusMapper campusMapper, TermMapper termMapper, TeacherMapper teacherMapper,
                                    CourseMapper courseMapper, ClassTypeMapper classTypeMapper,
                                    ClassStudentMapper classStudentMapper,
                                    ClassChangeNoticeService classChangeNoticeService,
                                    ClassTeacherService classTeacherService) {
        this.teachingClassMapper = teachingClassMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
        this.termMapper = termMapper;
        this.teacherMapper = teacherMapper;
        this.courseMapper = courseMapper;
        this.classTypeMapper = classTypeMapper;
        this.classStudentMapper = classStudentMapper;
        this.classChangeNoticeService = classChangeNoticeService;
        this.classTeacherService = classTeacherService;
    }

    @Override
    public List<TeachingClassEntity> listClasses(Long institutionId, Long campusId, Long termId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .orderByAsc(TeachingClassEntity::getInstitutionId)
            .orderByAsc(TeachingClassEntity::getCampusId)
            .orderByAsc(TeachingClassEntity::getClassCode);
        applyScope(wrapper, institutionId, campusId, termId);
        return teachingClassMapper.selectList(wrapper);
    }

    @Override
    public List<ClassOptionView> listClassOptions(Long institutionId, Long campusId, Long termId, String keyword,
                                                  List<Long> ids) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .select(TeachingClassEntity::getId, TeachingClassEntity::getClassCode, TeachingClassEntity::getClassName,
                TeachingClassEntity::getCampusId, TeachingClassEntity::getTermId);
        applyScope(wrapper, institutionId, campusId, termId);
        List<Long> distinctIds = ids == null ? List.of()
            : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        String trimmedKeyword = normalize(keyword);
        if (!distinctIds.isEmpty()) {
            // 按 id 回查用于给表格行补名称，关键字让位，否则传了 ids 也查不到
            wrapper.in(TeachingClassEntity::getId, distinctIds);
        } else if (trimmedKeyword != null && !trimmedKeyword.isEmpty()) {
            wrapper.and(w -> w
                .like(TeachingClassEntity::getClassName, trimmedKeyword)
                .or()
                .like(TeachingClassEntity::getClassCode, trimmedKeyword));
        }
        wrapper.orderByAsc(TeachingClassEntity::getCampusId).orderByAsc(TeachingClassEntity::getClassCode);
        wrapper.last("LIMIT " + (distinctIds.isEmpty() ? CLASS_OPTION_LIMIT : CLASS_LABEL_LIMIT));
        return teachingClassMapper.selectList(wrapper).stream()
            .map(item -> new ClassOptionView(item.getId(), item.getClassCode(), item.getClassName(),
                item.getCampusId(), item.getTermId()))
            .toList();
    }

    @Override
    public long countClasses(Long institutionId, Long campusId, Long termId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, institutionId, campusId, termId);
        Long count = teachingClassMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public TeachingClassEntity getClassDetail(Long id, Long institutionId) {
        TeachingClassEntity teachingClass = requireClass(id);
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于该机构：" + institutionId);
        }
        return teachingClass;
    }

    /** 班级查询的统一筛选口径：三个范围参数都是"传了才过滤"。 */
    private void applyScope(LambdaQueryWrapper<TeachingClassEntity> wrapper, Long institutionId, Long campusId, Long termId) {
        if (institutionId != null) {
            wrapper.eq(TeachingClassEntity::getInstitutionId, institutionId);
        }
        if (campusId != null) {
            wrapper.eq(TeachingClassEntity::getCampusId, campusId);
        }
        if (termId != null) {
            wrapper.eq(TeachingClassEntity::getTermId, termId);
        }
    }

    @Override
    @Transactional
    public TeachingClassEntity createClass(TeachingClassCreateRequest request) {
        validateRequest(request);
        int capacityLimit = normalizeCapacityLimit(request.capacityLimit());
        String classCode = normalize(request.classCode());
        String className = normalize(request.className());
        assertUniqueClassCode(request.campusId(), classCode, null);
        assertUniqueClassName(request.campusId(), className, null);

        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTermId(request.termId());
        entity.setCourseId(request.courseId());
        entity.setClassTypeId(request.classTypeId());
        entity.setClassCode(classCode);
        entity.setClassName(className);
        entity.setTeacherId(request.teacherId());
        entity.setCapacityLimit(capacityLimit);
        applyLessonTarget(entity, request);
        entity.setCurrentStudents(0);
        entity.setStatus(normalizeStatus(request.status()));
        entity.setOpenedAt(request.openedAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            teachingClassMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级编码或班级名称已存在：" + request.campusId());
        }
        // 主讲教师以班级教师关系为准：只写 jp_class.teacher_id 不建关系，班级详情页会提示"班级未设置主讲教师"
        classTeacherService.assignPrimaryTeacher(entity.getInstitutionId(), entity.getId(), request.teacherId());
        classChangeNoticeService.syncClassNotices(entity.getInstitutionId(), entity.getId());
        return entity;
    }

    @Override
    @Transactional
    public TeachingClassEntity updateClass(Long id, TeachingClassCreateRequest request) {
        TeachingClassEntity existing = requireClass(id);
        validateRequest(request);
        if (!Objects.equals(existing.getInstitutionId(), request.institutionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于该机构：" + request.institutionId());
        }
        int capacityLimit = normalizeCapacityLimit(request.capacityLimit());
        int activeStudents = countActiveStudents(id);
        if (activeStudents > capacityLimit) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "容量上限不能小于当前在班人数：" + activeStudents);
        }
        String classCode = normalize(request.classCode());
        String className = normalize(request.className());
        assertUniqueClassCode(request.campusId(), classCode, id);
        assertUniqueClassName(request.campusId(), className, id);

        existing.setInstitutionId(request.institutionId());
        existing.setCampusId(request.campusId());
        existing.setTermId(request.termId());
        existing.setCourseId(request.courseId());
        existing.setClassTypeId(request.classTypeId());
        existing.setClassCode(classCode);
        existing.setClassName(className);
        // 主讲教师不在这里直接改列，统一交给班级教师关系回写，避免两张表口径不一致
        existing.setCapacityLimit(capacityLimit);
        applyLessonTarget(existing, request);
        existing.setCurrentStudents(activeStudents);
        existing.setStatus(normalizeStatus(request.status()));
        existing.setOpenedAt(request.openedAt());
        existing.setRemark(request.remark());
        try {
            teachingClassMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级编码或班级名称已存在：" + request.campusId());
        }
        classTeacherService.assignPrimaryTeacher(existing.getInstitutionId(), existing.getId(), request.teacherId());
        existing.setTeacherId(request.teacherId());
        classChangeNoticeService.syncClassNotices(existing.getInstitutionId(), existing.getId());
        return existing;
    }

    @Override
    @Transactional
    public void deleteClass(Long id) {
        TeachingClassEntity existing = requireClass(id);
        Long activeStudents = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, id)
            .eq(ClassStudentEntity::getStatus, "active"));
        if (activeStudents != null && activeStudents > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "班级仍有在读学员：" + existing.getId() + "，请先移出班级学员");
        }
        teachingClassMapper.deleteById(id);
    }

    private void validateRequest(TeachingClassCreateRequest request) {
        requireInstitution(request.institutionId());
        requireCampusInInstitution(request.campusId(), request.institutionId());
        if (request.termId() != null) {
            requireTermInInstitution(request.termId(), request.institutionId());
        }
        if (request.courseId() != null) {
            requireCourseInInstitution(request.courseId(), request.institutionId());
        }
        if (request.classTypeId() != null) {
            requireClassTypeInInstitution(request.classTypeId(), request.institutionId());
        }
        if (request.teacherId() != null) {
            requireTeacherInInstitution(request.teacherId(), request.institutionId());
        }
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

    private void requireTermInInstitution(Long termId, Long institutionId) {
        TermEntity term = termMapper.selectById(termId);
        if (term == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在：" + termId);
        }
        if (!Objects.equals(term.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学期不属于该机构：" + institutionId);
        }
    }

    private void requireTeacherInInstitution(Long teacherId, Long institutionId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教师不存在：" + teacherId);
        }
        if (!Objects.equals(teacher.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "教师不属于该机构：" + institutionId);
        }
    }

    private void requireCourseInInstitution(Long courseId, Long institutionId) {
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在：" + courseId);
        }
        if (!Objects.equals(course.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "课程不属于该机构：" + institutionId);
        }
    }

    private void requireClassTypeInInstitution(Long classTypeId, Long institutionId) {
        ClassTypeEntity classType = classTypeMapper.selectById(classTypeId);
        if (classType == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班型不存在：" + classTypeId);
        }
        if (!Objects.equals(classType.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班型不属于该机构：" + institutionId);
        }
    }

    private void assertUniqueClassCode(Long campusId, String classCode, Long excludeId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getCampusId, campusId)
            .eq(TeachingClassEntity::getClassCode, classCode);
        if (excludeId != null) {
            wrapper.ne(TeachingClassEntity::getId, excludeId);
        }
        Long count = teachingClassMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级编码已存在：" + campusId);
        }
    }

    private void assertUniqueClassName(Long campusId, String className, Long excludeId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getCampusId, campusId)
            .eq(TeachingClassEntity::getClassName, className);
        if (excludeId != null) {
            wrapper.ne(TeachingClassEntity::getId, excludeId);
        }
        Long count = teachingClassMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级名称已存在：" + campusId);
        }
    }

    private TeachingClassEntity requireClass(Long id) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(id);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + id);
        }
        return teachingClass;
    }

    private int normalizeCapacityLimit(Integer capacityLimit) {
        return capacityLimit == null ? 0 : capacityLimit;
    }

    /**
     * 写入课时目标三列：口径与原值照用户填的存，{@code weekly_lessons} 一律由换算器得出。
     *
     * <p>请求里的 {@code weeklyLessons} 只在"没传原值"时充当原值，让只发这一个字段的旧调用方
     * 行为不变；口径为 monthly 时以原值换算为准，直发的 {@code weeklyLessons} 不生效。</p>
     */
    private void applyLessonTarget(TeachingClassEntity entity, TeachingClassCreateRequest request) {
        String mode = LessonTargetConverter.normalizeMode(request.weeklyLessonsMode());
        Integer input = request.weeklyLessonsInput() != null ? request.weeklyLessonsInput() : request.weeklyLessons();
        entity.setWeeklyLessonsMode(mode);
        entity.setWeeklyLessonsInput(input);
        entity.setWeeklyLessons(LessonTargetConverter.toWeekly(mode, input));
        entity.setTotalLessons(request.totalLessons());
    }

    private int countActiveStudents(Long classId) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStatus, "active"));
        return count == null ? 0 : count.intValue();
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "planning" : status.trim();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
