package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ScheduleConflictCheckRequest;
import com.cornerstone.module.education.dto.ScheduleConflictCheckResponse;
import com.cornerstone.module.education.dto.ScheduleLessonPageQuery;
import com.cornerstone.module.education.dto.ScheduleLessonRequest;
import com.cornerstone.module.education.dto.ScheduleLessonView;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.SchoolCalendarService;
import com.cornerstone.module.education.service.ScheduleLessonService;
import com.cornerstone.module.education.service.support.ScheduleInstitutionGuard;
import com.cornerstone.module.education.service.support.ScheduleRuleSupport;
import com.cornerstone.module.education.service.support.ScheduleTeacherLock;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleLessonServiceImpl implements ScheduleLessonService {

    private static final String SCHEDULED = "scheduled";
    private static final String RESCHEDULED = "rescheduled";
    private static final String CANCELLED = "cancelled";

    /** 课次列表可排序字段:班级/教室/教师是 ID 列,列表上显示的是名称,不参与排序 */
    private static final Map<String, SFunction<ScheduleLessonEntity, ?>> LESSON_SORTABLE = Map.of(
        "lessonDate", ScheduleLessonEntity::getLessonDate,
        "lessonWeekday", ScheduleLessonEntity::getLessonWeekday,
        "startTime", ScheduleLessonEntity::getStartTime,
        "endTime", ScheduleLessonEntity::getEndTime,
        "lessonTitle", ScheduleLessonEntity::getLessonTitle,
        "lessonStatus", ScheduleLessonEntity::getLessonStatus,
        "changeType", ScheduleLessonEntity::getChangeType,
        "remark", ScheduleLessonEntity::getRemark);
    private static final String MAKEUP = "makeup";
    private static final String COMPLETED = "completed";
    private static final String CANCELLED_MAKEUP = "cancelled_makeup";
    /** 跨机构访问被拒时的提示对象名：「无权访问其他机构的排课课次」 */
    private static final String SUBJECT = "排课课次";
    /** 入班关系里只有 active 算在册：预占(reserved)、已退班(quit)不参与学员跨班撞课判定 */
    private static final String CLASS_STUDENT_ACTIVE = "active";
    /** 学员跨班提示最多列出几个人：整班撞课时提示块会占满屏幕，多出来的用一行汇总 */
    private static final int NOTICE_STUDENT_LIMIT = 5;

    private final ScheduleLessonMapper lessonMapper;
    private final ScheduleTeacherRelMapper teacherRelMapper;
    private final TeachingClassMapper classMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentMapper studentMapper;
    private final SchoolCalendarService schoolCalendarService;
    private final ScheduleRuleSupport ruleSupport;
    private final ScheduleTeacherLock teacherLock;

    public ScheduleLessonServiceImpl(ScheduleLessonMapper lessonMapper, ScheduleTeacherRelMapper teacherRelMapper,
                                     TeachingClassMapper classMapper, TeacherMapper teacherMapper,
                                     ClassroomMapper classroomMapper, TimeSlotMapper timeSlotMapper,
                                     ClassStudentMapper classStudentMapper, StudentMapper studentMapper,
                                     SchoolCalendarService schoolCalendarService, ScheduleRuleSupport ruleSupport,
                                     ScheduleTeacherLock teacherLock) {
        this.lessonMapper = lessonMapper;
        this.teacherRelMapper = teacherRelMapper;
        this.classMapper = classMapper;
        this.teacherMapper = teacherMapper;
        this.classroomMapper = classroomMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.classStudentMapper = classStudentMapper;
        this.studentMapper = studentMapper;
        this.schoolCalendarService = schoolCalendarService;
        this.ruleSupport = ruleSupport;
        this.teacherLock = teacherLock;
    }

    @Override
    public List<ScheduleLessonView> listLessons(Long institutionId, Long campusId, Long classId, LocalDate lessonDate,
                                                Boolean pendingMakeupOnly) {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = baseOrder();
        wrapper.eq(ScheduleLessonEntity::getInstitutionId,
            ScheduleInstitutionGuard.resolveForQuery(institutionId, SUBJECT));
        if (campusId != null) {
            wrapper.eq(ScheduleLessonEntity::getCampusId, campusId);
        }
        if (classId != null) {
            wrapper.eq(ScheduleLessonEntity::getClassId, classId);
        }
        if (lessonDate != null) {
            wrapper.eq(ScheduleLessonEntity::getLessonDate, lessonDate);
        }
        return toViews(lessonMapper.selectList(wrapper), pendingMakeupOnly);
    }

    @Override
    public PageResult<ScheduleLessonView> pageLessons(ScheduleLessonPageQuery query) {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = new LambdaQueryWrapper<>();
        applyScopeFilters(wrapper, query);
        applyOrder(wrapper, query);
        Page<ScheduleLessonEntity> page = lessonMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords(), Boolean.FALSE));
    }

    @Override
    public PageResult<ScheduleLessonView> pagePendingMakeupLessons(ScheduleLessonPageQuery query) {
        // 待补课 = 已停课且没有任何"停课补课"课次关联,直接在 SQL 层收敛,避免全量拉取后内存过滤
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getLessonStatus, CANCELLED)
            .notExists("""
                SELECT 1 FROM schedule_lesson linked
                WHERE linked.deleted = 0
                  AND linked.lesson_status = 'makeup'
                  AND linked.change_type = 'cancelled_makeup'
                  AND linked.related_lesson_id = schedule_lesson.id
                """);
        applyScopeFilters(wrapper, query);
        applyOrder(wrapper, query);
        Page<ScheduleLessonEntity> page = lessonMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords(), Boolean.FALSE));
    }

    /** 分页列表的排序:前端传了白名单内的字段就按它排,否则沿用默认的上课时间顺序 */
    private void applyOrder(LambdaQueryWrapper<ScheduleLessonEntity> wrapper, ScheduleLessonPageQuery query) {
        if (!PageSortSupport.apply(wrapper, query, LESSON_SORTABLE, ScheduleLessonEntity::getId)) {
            applyDefaultOrder(wrapper);
        }
    }

    private void applyScopeFilters(LambdaQueryWrapper<ScheduleLessonEntity> wrapper, ScheduleLessonPageQuery query) {
        wrapper.eq(ScheduleLessonEntity::getInstitutionId,
            ScheduleInstitutionGuard.resolveForQuery(query.getInstitutionId(), SUBJECT));
        if (query.getCampusId() != null) {
            wrapper.eq(ScheduleLessonEntity::getCampusId, query.getCampusId());
        }
        if (query.getClassId() != null) {
            wrapper.eq(ScheduleLessonEntity::getClassId, query.getClassId());
        }
        if (query.getPlanId() != null) {
            wrapper.eq(ScheduleLessonEntity::getPlanId, query.getPlanId());
        }
        if (query.getLessonDate() != null) {
            wrapper.eq(ScheduleLessonEntity::getLessonDate, query.getLessonDate());
        }
    }

    @Override
    public ScheduleLessonView getLesson(Long id) {
        return toViews(List.of(requireLesson(id)), Boolean.FALSE).getFirst();
    }

    @Override
    @Transactional
    public ScheduleLessonView createLesson(ScheduleLessonRequest request) {
        ScheduleMaterial material = validateMaterial(request, null);
        teacherLock.lock(request.teacherIds());
        assertNoConflicts(request, null);
        ScheduleLessonEntity entity = toEntity(request, material);
        entity.setDeleted(Boolean.FALSE);
        try {
            lessonMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw slotTakenException();
        }
        replaceTeachers(entity.getId(), request.teacherIds());
        return toViews(List.of(entity), Boolean.FALSE).getFirst();
    }

    @Override
    @Transactional
    public ScheduleLessonView updateLesson(Long id, ScheduleLessonRequest request) {
        requireLesson(id);
        ScheduleMaterial material = validateMaterial(request, id);
        teacherLock.lock(request.teacherIds());
        assertNoConflicts(request, id);
        assertMakeupSourceIntact(id, defaultStatus(request.lessonStatus()));
        ScheduleLessonEntity entity = toEntity(request, material);
        entity.setId(id);
        try {
            lessonMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw slotTakenException();
        }
        replaceTeachers(id, request.teacherIds());
        return toViews(List.of(requireLesson(id)), Boolean.FALSE).getFirst();
    }

    @Override
    @Transactional
    public void deleteLesson(Long id) {
        requireLesson(id);
        // null 表示课次将不存在：指向它的补课行会悬空
        assertMakeupSourceIntact(id, null);
        teacherRelMapper.physicalDeleteByLessonId(id);
        lessonMapper.deleteById(id);
    }

    @Override
    public List<ScheduleLessonView> dayTimetable(Long institutionId, Long campusId, Long classId, LocalDate lessonDate,
                                                 Boolean pendingMakeupOnly) {
        if (lessonDate == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上课日期不能为空");
        }
        return listLessons(institutionId, campusId, classId, lessonDate, pendingMakeupOnly);
    }

    @Override
    public List<ScheduleLessonView> weekTimetable(Long institutionId, Long campusId, Long classId, LocalDate weekStart,
                                                  LocalDate weekEnd, Boolean pendingMakeupOnly) {
        if (weekStart == null || weekEnd == null || weekStart.isAfter(weekEnd)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "周起始日期和周结束日期必须为有效日期，且周起始日期不能晚于周结束日期");
        }
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = baseOrder()
            .between(ScheduleLessonEntity::getLessonDate, weekStart, weekEnd)
            .eq(ScheduleLessonEntity::getInstitutionId,
                ScheduleInstitutionGuard.resolveForQuery(institutionId, SUBJECT));
        if (campusId != null) {
            wrapper.eq(ScheduleLessonEntity::getCampusId, campusId);
        }
        if (classId != null) {
            wrapper.eq(ScheduleLessonEntity::getClassId, classId);
        }
        return toViews(lessonMapper.selectList(wrapper), pendingMakeupOnly);
    }

    @Override
    public ScheduleConflictCheckResponse checkConflicts(ScheduleConflictCheckRequest request) {
        ScheduleLessonRequest lessonRequest = new ScheduleLessonRequest(
            request.institutionId(),
            request.campusId(),
            request.termId(),
            request.classId(),
            request.classroomId(),
            request.timeSlotId(),
            request.lessonDate(),
            null,
            SCHEDULED,
            null,
            null,
            request.teacherIds(),
            null
        );
        validateMaterial(lessonRequest, null);
        return detectConflicts(lessonRequest, request.excludeLessonId());
    }

    private ScheduleMaterial validateMaterial(ScheduleLessonRequest request, Long currentLessonId) {
        // 写入口的机构一律先与登录机构比对，下面所有"资料归属"校验才有意义
        ScheduleInstitutionGuard.assertOwn(request.institutionId(), SUBJECT);
        TeachingClassEntity teachingClass = classMapper.selectById(request.classId());
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + request.classId());
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), request.institutionId())
            || !Objects.equals(teachingClass.getTermId(), request.termId())
            || !Objects.equals(teachingClass.getCampusId(), request.campusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级的学期/校区/机构与课次不一致");
        }
        ClassroomEntity classroom = classroomMapper.selectById(request.classroomId());
        if (classroom == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教室不存在：" + request.classroomId());
        }
        if (!Objects.equals(classroom.getCampusId(), request.campusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "教室校区与课次不一致");
        }
        TimeSlotEntity timeSlot = timeSlotMapper.selectById(request.timeSlotId());
        if (timeSlot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "时间段不存在：" + request.timeSlotId());
        }
        if (!Objects.equals(timeSlot.getInstitutionId(), request.institutionId())
            || (timeSlot.getCampusId() != null && !Objects.equals(timeSlot.getCampusId(), request.campusId()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时间段与课次不匹配");
        }
        Integer weekday = request.lessonDate().getDayOfWeek().getValue();
        if (!Objects.equals(timeSlot.getWeekday(), weekday)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "课次日期的星期与时间段星期不一致");
        }
        Set<Long> teacherIds = distinctTeacherIds(request.teacherIds());
        if (teacherIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "至少需要一名授课教师");
        }
        for (Long teacherId : teacherIds) {
            TeacherEntity teacher = teacherMapper.selectById(teacherId);
            if (teacher == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "教师不存在：" + teacherId);
            }
            if (!Objects.equals(teacher.getInstitutionId(), request.institutionId())
                || !Objects.equals(teacher.getCampusId(), request.campusId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "教师的机构/校区与课次不一致");
            }
        }
        validateLessonLinkage(request, teachingClass, currentLessonId);
        return new ScheduleMaterial(weekday, timeSlot.getStartTime(), timeSlot.getEndTime());
    }

    private void validateLessonLinkage(ScheduleLessonRequest request, TeachingClassEntity teachingClass, Long currentLessonId) {
        String lessonStatus = defaultStatus(request.lessonStatus());
        String changeType = normalizeChangeType(request.changeType());
        Long relatedLessonId = request.relatedLessonId();

        if (!MAKEUP.equalsIgnoreCase(lessonStatus)) {
            if (changeType != null || relatedLessonId != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "只有补课课次才允许填写变更类型和关联的原课次");
            }
            return;
        }

        if (changeType == null && relatedLessonId == null) {
            return;
        }

        if (!Objects.equals(changeType, CANCELLED_MAKEUP) || relatedLessonId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "补课关联信息不完整，需要同时提供停课补课的变更类型和关联的原课次");
        }
        if (currentLessonId != null && Objects.equals(currentLessonId, relatedLessonId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "关联的原课次不能是当前课次本身");
        }

        ScheduleLessonEntity relatedLesson = requireLesson(relatedLessonId);
        if (!Objects.equals(relatedLesson.getInstitutionId(), request.institutionId())
            || !Objects.equals(relatedLesson.getCampusId(), request.campusId())
            || !Objects.equals(relatedLesson.getTermId(), request.termId())
            || !Objects.equals(relatedLesson.getClassId(), teachingClass.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "关联原课次不属于同一班级范围");
        }
        if (!CANCELLED.equalsIgnoreCase(relatedLesson.getLessonStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "关联的原课次必须是已停课课次，才能安排补课");
        }
    }

    /**
     * 原课次被补课行引用时的反向校验：补课成立的前提是"原课次已停课"，
     * 把原课次改成结课/已排课会让补课行的 {@code cancelled_makeup} 名义与实际状态矛盾，
     * 删除原课次则让课表上的「查看原停课」直接悬空。
     *
     * @param newStatus 课次要改成的状态；{@code null} 表示课次将被删除
     */
    private void assertMakeupSourceIntact(Long lessonId, String newStatus) {
        if (CANCELLED.equalsIgnoreCase(newStatus)) {
            return;
        }
        Long dependents = lessonMapper.selectCount(new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getRelatedLessonId, lessonId)
            .eq(ScheduleLessonEntity::getChangeType, CANCELLED_MAKEUP));
        if (dependents != null && dependents > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "已有 " + dependents + " 节补课课次关联本课次，请先处理这些补课课次（改期或删除），再调整本课次的状态");
        }
    }

    private void assertNoConflicts(ScheduleLessonRequest request, Long excludeLessonId) {
        if (!occupiesSchedulingSlot(defaultStatus(request.lessonStatus()))) {
            return;
        }
        ScheduleConflictCheckResponse response = detectConflicts(request, excludeLessonId);
        if (Boolean.TRUE.equals(response.conflict())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, String.join("; ", response.messages()));
        }
    }

    private ScheduleConflictCheckResponse detectConflicts(ScheduleLessonRequest request, Long excludeLessonId) {
        boolean checkRules = ruleRelevantFieldsChanged(excludeLessonId, request);
        List<String> types = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        Long classCount = lessonMapper.selectCount(conflictWrapper(excludeLessonId, request.institutionId())
            .eq(ScheduleLessonEntity::getClassId, request.classId())
            .eq(ScheduleLessonEntity::getLessonDate, request.lessonDate())
            .eq(ScheduleLessonEntity::getTimeSlotId, request.timeSlotId()));
        if (classCount != null && classCount > 0) {
            types.add("class");
            messages.add("该班级在此时段已有排课");
        }
        Long classroomCount = lessonMapper.selectCount(conflictWrapper(excludeLessonId, request.institutionId())
            .eq(ScheduleLessonEntity::getClassroomId, request.classroomId())
            .eq(ScheduleLessonEntity::getLessonDate, request.lessonDate())
            .eq(ScheduleLessonEntity::getTimeSlotId, request.timeSlotId()));
        if (classroomCount != null && classroomCount > 0) {
            types.add("classroom");
            messages.add("该教室在此时段已被占用");
        }
        for (Long teacherId : distinctTeacherIds(request.teacherIds())) {
            Long teacherCount = teacherRelMapper.countTeacherConflicts(
                teacherId,
                request.lessonDate(),
                request.timeSlotId(),
                excludeLessonId
            );
            if (teacherCount != null && teacherCount > 0) {
                types.add("teacher");
                messages.add("教师「" + teacherLabel(teacherId) + "」在此时段已被占用");
            }
        }
        List<String> notices = new ArrayList<>();
        // 禁排规则与占用同层判定：撞上了既不让单独排课，也不让在冲突检查里被忽略
        // 例外：改一节课时班级/日期/时段/教师都没动，就不拿规则回头拦它——课次可以早于规则建立，
        // 那种冲突是历史事实，操作员要能改个备注、换个教室把它挪出窗口，而不是整节保存不了
        if (checkRules) {
            List<String> ruleMessages = ruleSupport.loadRuleSet(request.institutionId(),
                    List.of(request.classId()), List.copyOf(distinctTeacherIds(request.teacherIds())),
                    request.classroomId() == null ? List.of() : List.of(request.classroomId()))
                .forbiddenMessages(request.lessonDate(), request.lessonDate().getDayOfWeek().getValue(),
                    request.timeSlotId());
            if (!ruleMessages.isEmpty()) {
                types.add("rule");
                messages.addAll(ruleMessages);
            }
        }
        String closedNotice = schoolCalendarService.closedDayNotice(request.institutionId(),
            request.campusId(), request.lessonDate());
        if (closedNotice != null) {
            notices.add(closedNotice);
        }
        notices.addAll(teacherLoadNotices(request, excludeLessonId));
        notices.addAll(studentClashNotices(request));
        return new ScheduleConflictCheckResponse(!types.isEmpty(), types, messages, notices);
    }

    /**
     * 教师课时上限的提醒：把这一节算进去才判，超了也只提示不拦。
     * 加班课、临时加课在本行业是常态，硬拦等于逼操作员先把上限改大再改回来。
     */
    private List<String> teacherLoadNotices(ScheduleLessonRequest request, Long excludeLessonId) {
        List<String> notices = new ArrayList<>();
        if (request.lessonDate() == null) {
            return notices;
        }
        LocalDate date = request.lessonDate();
        LocalDate weekStart = date.minusDays(date.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);
        for (Long teacherId : distinctTeacherIds(request.teacherIds())) {
            TeacherEntity teacher = teacherMapper.selectById(teacherId);
            if (teacher == null) {
                continue;
            }
            String label = teacherDisplayName(teacher, teacherId);
            if (teacher.getMaxDailyLessons() != null) {
                long scheduled = countTeacherLessons(teacherId, date, date, excludeLessonId);
                if (scheduled + 1 > teacher.getMaxDailyLessons()) {
                    notices.add("教师「" + label + "」当日已排 " + scheduled + " 节，本节排入将达 "
                        + (scheduled + 1) + " 节，超出每日上限 " + teacher.getMaxDailyLessons() + "（只提示不拦）");
                }
            }
            if (teacher.getMaxWeeklyLessons() != null) {
                long scheduled = countTeacherLessons(teacherId, weekStart, weekEnd, excludeLessonId);
                if (scheduled + 1 > teacher.getMaxWeeklyLessons()) {
                    notices.add("教师「" + label + "」本周已排 " + scheduled + " 节，本节排入将达 "
                        + (scheduled + 1) + " 节，超出周课时上限 " + teacher.getMaxWeeklyLessons() + "（只提示不拦）");
                }
            }
        }
        return notices;
    }

    private long countTeacherLessons(Long teacherId, LocalDate start, LocalDate end, Long excludeLessonId) {
        Long count = teacherRelMapper.countTeacherLessonsInRange(teacherId, start, end, excludeLessonId);
        return count == null ? 0L : count;
    }

    /**
     * 学员跨班撞课：本班学员在同一日期同一时段还挂着别班的课。
     * 只提示不拦——插班试听、临时借读、串班上课都是真场景，硬拦会把正常操作挡死。
     */
    private List<String> studentClashNotices(ScheduleLessonRequest request) {
        if (request.classId() == null || request.lessonDate() == null || request.timeSlotId() == null) {
            return List.of();
        }
        List<Long> studentIds = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudentEntity>()
                .select(ClassStudentEntity::getStudentId)
                .eq(ClassStudentEntity::getClassId, request.classId())
                .eq(ClassStudentEntity::getStatus, CLASS_STUDENT_ACTIVE))
            .stream().map(ClassStudentEntity::getStudentId).filter(Objects::nonNull).distinct().toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        List<ClassStudentEntity> otherEnrollments = classStudentMapper.selectList(
            new LambdaQueryWrapper<ClassStudentEntity>()
                .select(ClassStudentEntity::getStudentId, ClassStudentEntity::getClassId)
                .in(ClassStudentEntity::getStudentId, studentIds)
                .eq(ClassStudentEntity::getStatus, CLASS_STUDENT_ACTIVE)
                .ne(ClassStudentEntity::getClassId, request.classId()));
        if (otherEnrollments.isEmpty()) {
            return List.of();
        }
        List<Long> otherClassIds = otherEnrollments.stream().map(ClassStudentEntity::getClassId)
            .filter(Objects::nonNull).distinct().toList();
        Set<Long> clashingClassIds = new HashSet<>(lessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLessonEntity>()
                    .select(ScheduleLessonEntity::getClassId)
                    .eq(ScheduleLessonEntity::getInstitutionId, request.institutionId())
                    .in(ScheduleLessonEntity::getClassId, otherClassIds)
                    .eq(ScheduleLessonEntity::getLessonDate, request.lessonDate())
                    .eq(ScheduleLessonEntity::getTimeSlotId, request.timeSlotId())
                    .in(ScheduleLessonEntity::getLessonStatus, SCHEDULED, RESCHEDULED, MAKEUP))
            .stream().map(ScheduleLessonEntity::getClassId).filter(Objects::nonNull).toList());
        if (clashingClassIds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> classesByStudent = otherEnrollments.stream()
            .filter(item -> clashingClassIds.contains(item.getClassId()))
            .collect(java.util.stream.Collectors.groupingBy(ClassStudentEntity::getStudentId,
                java.util.stream.Collectors.mapping(ClassStudentEntity::getClassId,
                    java.util.stream.Collectors.toList())));
        if (classesByStudent.isEmpty()) {
            return List.of();
        }
        Map<Long, String> classNames = classNames(clashingClassIds);
        Map<Long, String> studentNames = studentNames(classesByStudent.keySet());
        List<String> notices = new ArrayList<>();
        classesByStudent.entrySet().stream().limit(NOTICE_STUDENT_LIMIT).forEach(entry -> {
            String students = studentNames.getOrDefault(entry.getKey(), "#" + entry.getKey());
            String classes = entry.getValue().stream().distinct()
                .map(classId -> "「" + classNames.getOrDefault(classId, "#" + classId) + "」")
                .collect(java.util.stream.Collectors.joining("、"));
            notices.add("学员「" + students + "」同时段在" + classes + "还有课（只提示不拦）");
        });
        if (classesByStudent.size() > NOTICE_STUDENT_LIMIT) {
            notices.add("另有 " + (classesByStudent.size() - NOTICE_STUDENT_LIMIT) + " 名学员同时段在他班有课（只提示不拦）");
        }
        return notices;
    }

    private Map<Long, String> classNames(Set<Long> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectList(new LambdaQueryWrapper<TeachingClassEntity>()
                .select(TeachingClassEntity::getId, TeachingClassEntity::getClassName, TeachingClassEntity::getClassCode)
                .in(TeachingClassEntity::getId, classIds))
            .stream().collect(java.util.stream.Collectors.toMap(TeachingClassEntity::getId,
                item -> item.getClassName() == null || item.getClassName().isBlank()
                    ? Objects.toString(item.getClassCode(), "#" + item.getId()) : item.getClassName()));
    }

    private Map<Long, String> studentNames(Set<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .select(StudentEntity::getId, StudentEntity::getStudentName)
                .in(StudentEntity::getId, studentIds))
            .stream().filter(item -> item.getStudentName() != null && !item.getStudentName().isBlank())
            .collect(java.util.stream.Collectors.toMap(StudentEntity::getId, StudentEntity::getStudentName,
                (first, second) -> first));
    }

    /** 教师显示名：提示语里不出现 19 位雪花 ID。 */
    private String teacherDisplayName(TeacherEntity teacher, Long teacherId) {
        if (teacher.getTeacherName() != null && !teacher.getTeacherName().isBlank()) {
            return teacher.getTeacherName();
        }
        return teacher.getTeacherCode() == null || teacher.getTeacherCode().isBlank()
            ? "#" + teacherId : teacher.getTeacherCode();
    }

    /** 冲突说明里的教师身份：19 位雪花 ID 操作员认不出来，报姓名、其次编号，都查不到才退回 ID。 */
    private String teacherLabel(Long teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            return "#" + teacherId;
        }
        if (teacher.getTeacherName() != null && !teacher.getTeacherName().isBlank()) {
            return teacher.getTeacherName();
        }
        return teacher.getTeacherCode() == null || teacher.getTeacherCode().isBlank()
            ? "#" + teacherId
            : teacher.getTeacherCode();
    }

    /** 编辑现有课次时，禁排判定涉及的四个字段是否被改动过；未改动或课次不存在时按"要判规则"处理。 */
    private boolean ruleRelevantFieldsChanged(Long excludeLessonId, ScheduleLessonRequest request) {
        if (excludeLessonId == null) {
            return true;
        }
        ScheduleLessonEntity existing = lessonMapper.selectById(excludeLessonId);
        if (existing == null || !Objects.equals(existing.getInstitutionId(), request.institutionId())) {
            return true;
        }
        boolean sameSlot = Objects.equals(existing.getClassId(), request.classId())
            && Objects.equals(existing.getLessonDate(), request.lessonDate())
            && Objects.equals(existing.getTimeSlotId(), request.timeSlotId());
        if (!sameSlot) {
            return true;
        }
        // 教师比的是集合：关系表按插入顺序返回，弹窗里的顺序变化不该被当成"换了人"
        Set<Long> storedTeachers = new HashSet<>(loadTeacherIds(List.of(existing))
            .getOrDefault(excludeLessonId, List.of()));
        return !storedTeachers.equals(distinctTeacherIds(request.teacherIds()));
    }

    private LambdaQueryWrapper<ScheduleLessonEntity> conflictWrapper(Long excludeLessonId, Long institutionId) {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getInstitutionId, institutionId)
            .in(ScheduleLessonEntity::getLessonStatus, SCHEDULED, RESCHEDULED, MAKEUP);
        if (excludeLessonId != null) {
            wrapper.ne(ScheduleLessonEntity::getId, excludeLessonId);
        }
        return wrapper;
    }

    private ScheduleLessonEntity toEntity(ScheduleLessonRequest request, ScheduleMaterial material) {
        ScheduleLessonEntity entity = new ScheduleLessonEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTermId(request.termId());
        entity.setClassId(request.classId());
        entity.setClassroomId(request.classroomId());
        entity.setTimeSlotId(request.timeSlotId());
        entity.setLessonDate(request.lessonDate());
        entity.setLessonWeekday(material.weekday());
        entity.setStartTime(material.startTime());
        entity.setEndTime(material.endTime());
        entity.setLessonTitle(normalize(request.lessonTitle()));
        entity.setLessonStatus(defaultStatus(request.lessonStatus()));
        entity.setChangeType(normalizeChangeType(request.changeType()));
        entity.setRelatedLessonId(request.relatedLessonId());
        entity.setRemark(request.remark());
        return entity;
    }

    private void replaceTeachers(Long lessonId, List<Long> teacherIds) {
        teacherRelMapper.physicalDeleteByLessonId(lessonId);
        for (Long teacherId : distinctTeacherIds(teacherIds)) {
            ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
            relation.setLessonId(lessonId);
            relation.setTeacherId(teacherId);
            relation.setDeleted(Boolean.FALSE);
            teacherRelMapper.insert(relation);
        }
    }

    private List<ScheduleLessonView> toViews(List<ScheduleLessonEntity> entities, Boolean pendingMakeupOnly) {
        Set<Long> cancelledLessonIds = entities.stream()
            .filter(entity -> CANCELLED.equalsIgnoreCase(entity.getLessonStatus()))
            .map(ScheduleLessonEntity::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Long> linkedLessonIds = cancelledLessonIds.isEmpty()
            ? List.of()
            : lessonMapper.selectLinkedCancelledLessonIds(List.copyOf(cancelledLessonIds));
        Set<Long> linkedCancelledLessonIds = linkedLessonIds == null
            ? Set.of()
            : new HashSet<>(linkedLessonIds);

        // 教师与禁排规则都按批取一次：整周课表逐条查库是 N+1，而规则要落到课次自己的班级与教师身上才算得准
        Map<Long, List<Long>> teacherIdsByLesson = loadTeacherIds(entities);
        Map<Long, List<String>> ruleMessagesByLesson = loadRuleConflicts(entities, teacherIdsByLesson);

        return entities.stream()
            .map(entity -> toView(entity, linkedCancelledLessonIds,
                teacherIdsByLesson.getOrDefault(entity.getId(), List.of()),
                ruleMessagesByLesson.getOrDefault(entity.getId(), List.of())))
            .filter(view -> !Boolean.TRUE.equals(pendingMakeupOnly) || Boolean.TRUE.equals(view.pendingMakeup()))
            .toList();
    }

    /** 一次取回这批课次的授课教师，键为课次ID */
    private Map<Long, List<Long>> loadTeacherIds(List<ScheduleLessonEntity> entities) {
        List<Long> lessonIds = entities.stream()
            .map(ScheduleLessonEntity::getId)
            .filter(Objects::nonNull)
            .toList();
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return teacherRelMapper.selectList(new LambdaQueryWrapper<ScheduleTeacherRelEntity>()
                .in(ScheduleTeacherRelEntity::getLessonId, lessonIds))
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(ScheduleTeacherRelEntity::getLessonId,
                java.util.stream.Collectors.mapping(ScheduleTeacherRelEntity::getTeacherId,
                    java.util.stream.Collectors.toList())));
    }

    /**
     * 标出踩在禁排格子上的课次：禁排规则可以晚于课次建立，"规则不回改已生成课次"之后这些课次就成了隐形冲突，
     * 只有读侧带上说明，操作员才看得见为什么这一节保存不了。
     */
    private Map<Long, List<String>> loadRuleConflicts(List<ScheduleLessonEntity> entities,
                                                      Map<Long, List<Long>> teacherIdsByLesson) {
        if (entities.isEmpty()) {
            return Map.of();
        }
        List<Long> teacherIds = teacherIdsByLesson.values().stream().flatMap(List::stream).distinct().toList();
        ScheduleRuleSupport.RuleSet ruleSet = ruleSupport.loadRuleSet(
            entities.getFirst().getInstitutionId(),
            entities.stream().map(ScheduleLessonEntity::getClassId).filter(Objects::nonNull).distinct().toList(),
            teacherIds,
            entities.stream().map(ScheduleLessonEntity::getClassroomId).filter(Objects::nonNull).distinct().toList());
        if (ruleSet.rules().isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> messages = new java.util.LinkedHashMap<>();
        for (ScheduleLessonEntity entity : entities) {
            List<String> hits = ruleSet.scopedTo(entity.getClassId(), teacherIdsByLesson.get(entity.getId()),
                    entity.getClassroomId())
                .forbiddenMessages(entity.getLessonDate(), entity.getLessonWeekday(), entity.getTimeSlotId());
            if (!hits.isEmpty()) {
                messages.put(entity.getId(), hits);
            }
        }
        return messages;
    }

    private ScheduleLessonView toView(ScheduleLessonEntity entity, Set<Long> linkedCancelledLessonIds,
                                      List<Long> teacherIds, List<String> ruleConflictMessages) {
        boolean pendingMakeup = CANCELLED.equalsIgnoreCase(entity.getLessonStatus())
            && !linkedCancelledLessonIds.contains(entity.getId());
        return new ScheduleLessonView(
            entity.getId(),
            entity.getInstitutionId(),
            entity.getCampusId(),
            entity.getTermId(),
            entity.getClassId(),
            entity.getClassroomId(),
            entity.getTimeSlotId(),
            entity.getPlanId(),
            entity.getLessonDate(),
            entity.getLessonWeekday(),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getLessonTitle(),
            entity.getLessonStatus(),
            entity.getChangeType(),
            entity.getRelatedLessonId(),
            pendingMakeup,
            teacherIds,
            entity.getRemark(),
            ruleConflictMessages
        );
    }

    private ScheduleLessonEntity requireLesson(Long id) {
        ScheduleLessonEntity entity = lessonMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "排课课次不存在：" + id);
        }
        ScheduleInstitutionGuard.assertOwn(entity.getInstitutionId(), SUBJECT);
        return entity;
    }

    private LambdaQueryWrapper<ScheduleLessonEntity> baseOrder() {
        LambdaQueryWrapper<ScheduleLessonEntity> wrapper = new LambdaQueryWrapper<>();
        applyDefaultOrder(wrapper);
        return wrapper;
    }

    /** 课次默认排序:先按日期再按开始时间 */
    private void applyDefaultOrder(LambdaQueryWrapper<ScheduleLessonEntity> wrapper) {
        wrapper.orderByAsc(ScheduleLessonEntity::getLessonDate)
            .orderByAsc(ScheduleLessonEntity::getStartTime);
    }

    private Set<Long> distinctTeacherIds(List<Long> teacherIds) {
        return new LinkedHashSet<>(teacherIds == null ? List.of() : teacherIds);
    }

    private String defaultStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null || normalized.isBlank()) {
            return SCHEDULED;
        }
        String canonical = normalized.toLowerCase();
        if (!List.of(SCHEDULED, RESCHEDULED, CANCELLED, MAKEUP, COMPLETED).contains(canonical)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "课次状态只能是已排课、已调课、已停课、已补课、已结课之一");
        }
        return canonical;
    }

    private String normalizeChangeType(String changeType) {
        String normalized = normalize(changeType);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String canonical = normalized.toLowerCase();
        if (!CANCELLED_MAKEUP.equals(canonical)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "变更类型只能为停课补课");
        }
        return canonical;
    }

    private boolean occupiesSchedulingSlot(String lessonStatus) {
        return SCHEDULED.equalsIgnoreCase(lessonStatus)
            || RESCHEDULED.equalsIgnoreCase(lessonStatus)
            || MAKEUP.equalsIgnoreCase(lessonStatus);
    }

    /**
     * 撞数据库唯一键时的中文提示。上面的冲突校验是先查后插，双页签或直调接口能在校验通过后同时落进同一格子，
     * 兜底的唯一约束是 schedule_lesson.class_slot_guard / classroom_slot_guard（见 V72 迁移）。
     */
    private BusinessException slotTakenException() {
        return new BusinessException(ResultCode.BAD_REQUEST,
            "该班级或教室在此时段已有课次（并发写入被数据库唯一约束拦下），请刷新后重试");
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private record ScheduleMaterial(Integer weekday, LocalTime startTime, LocalTime endTime) {
    }
}
