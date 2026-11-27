package com.cornerstone.module.education.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.SchedulePlanEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.SchedulePlanMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.schedule.dto.SchedulePlanCommitRequest;
import com.cornerstone.module.education.schedule.dto.SchedulePlanCommitResponse;
import com.cornerstone.module.education.schedule.dto.SchedulePlanOccurrence;
import com.cornerstone.module.education.schedule.dto.SchedulePlanPageQuery;
import com.cornerstone.module.education.schedule.dto.SchedulePlanPreviewResponse;
import com.cornerstone.module.education.schedule.dto.SchedulePlanRequest;
import com.cornerstone.module.education.schedule.dto.SchedulePlanView;
import com.cornerstone.module.education.schedule.dto.ScheduleSlotCell;
import com.cornerstone.module.education.schedule.service.SchedulePlanService;
import com.cornerstone.module.education.schedule.service.SchoolCalendarService;
import com.cornerstone.module.education.schedule.support.ScheduleInstitutionGuard;
import com.cornerstone.module.education.schedule.support.SchedulePlanExpander;
import com.cornerstone.module.education.schedule.support.ScheduleRuleSupport;
import com.cornerstone.module.education.schedule.support.ScheduleTeacherLock;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulePlanServiceImpl implements SchedulePlanService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_INACTIVE = "inactive";
    private static final List<String> OCCUPYING_STATUSES = List.of("scheduled", "rescheduled", "makeup");
    /** 跨机构访问被拒时的提示对象名：「无权访问其他机构的排课计划」 */
    private static final String SUBJECT = "排课计划";

    /** 排课计划列表可排序字段:班级/教师/教室是 ID 列,列表上显示的是名称,不参与排序 */
    private static final Map<String, SFunction<SchedulePlanEntity, ?>> PLAN_SORTABLE = Map.of(
        "startDate", SchedulePlanEntity::getStartDate,
        "endDate", SchedulePlanEntity::getEndDate,
        "weekFrequency", SchedulePlanEntity::getWeekFrequency,
        "status", SchedulePlanEntity::getStatus,
        "createdAt", SchedulePlanEntity::getCreatedAt);

    private final SchedulePlanMapper planMapper;
    private final TeachingClassMapper classMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ScheduleLessonMapper lessonMapper;
    private final ScheduleTeacherRelMapper teacherRelMapper;
    private final ScheduleRuleSupport ruleSupport;
    private final SchoolCalendarService schoolCalendarService;
    private final ScheduleTeacherLock teacherLock;
    private final ObjectMapper objectMapper;

    public SchedulePlanServiceImpl(SchedulePlanMapper planMapper, TeachingClassMapper classMapper,
                                   TeacherMapper teacherMapper, ClassroomMapper classroomMapper,
                                   TimeSlotMapper timeSlotMapper, ScheduleLessonMapper lessonMapper,
                                   ScheduleTeacherRelMapper teacherRelMapper, ScheduleRuleSupport ruleSupport,
                                   SchoolCalendarService schoolCalendarService, ScheduleTeacherLock teacherLock,
                                   ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.classMapper = classMapper;
        this.teacherMapper = teacherMapper;
        this.classroomMapper = classroomMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.lessonMapper = lessonMapper;
        this.teacherRelMapper = teacherRelMapper;
        this.ruleSupport = ruleSupport;
        this.schoolCalendarService = schoolCalendarService;
        this.teacherLock = teacherLock;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<SchedulePlanView> pagePlans(SchedulePlanPageQuery query) {
        LambdaQueryWrapper<SchedulePlanEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchedulePlanEntity::getInstitutionId,
            ScheduleInstitutionGuard.resolveForQuery(query.getInstitutionId(), SUBJECT));
        if (query.getCampusId() != null) {
            CampusScopeGuard.assertCampus(query.getCampusId(), "排课计划");
            wrapper.eq(SchedulePlanEntity::getCampusId, query.getCampusId());
        }
        CampusScopeGuard.apply(wrapper, SchedulePlanEntity::getCampusId);
        if (query.getClassId() != null) {
            wrapper.eq(SchedulePlanEntity::getClassId, query.getClassId());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(SchedulePlanEntity::getStatus, query.getStatus().trim());
        }
        if (query.getActiveFrom() != null) {
            wrapper.ge(SchedulePlanEntity::getEndDate, query.getActiveFrom());
        }
        if (query.getActiveTo() != null) {
            wrapper.le(SchedulePlanEntity::getStartDate, query.getActiveTo());
        }
        if (!PageSortSupport.apply(wrapper, query, PLAN_SORTABLE, SchedulePlanEntity::getId)) {
            wrapper.orderByDesc(SchedulePlanEntity::getCreatedAt);
        }
        Page<SchedulePlanEntity> page = planMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toView).toList());
    }

    @Override
    public SchedulePlanView getPlan(Long id) {
        return toView(requirePlan(id));
    }

    @Override
    public SchedulePlanPreviewResponse preview(SchedulePlanRequest request) {
        ResolvedPlan resolved = resolve(request);
        List<SchedulePlanExpander.Occurrence> occurrences = expand(resolved, request);
        ScheduleRuleSupport.RuleSet ruleSet = ruleSupport.loadRuleSet(
            request.institutionId(), List.of(request.classId()), resolved.teacherIds(),
            request.classroomId() == null ? List.of() : List.of(request.classroomId()));
        Occupancy occupancy = loadOccupancy(request.classId(), request.classroomId(), resolved.teacherIds(),
            request.startDate(), request.endDate(), null);
        Map<LocalDate, String> closedDays = schoolCalendarService.closedDays(
            request.institutionId(), request.campusId(), request.startDate(), request.endDate());
        Map<LocalDate, Integer> lessonsOnClosedDay = new LinkedHashMap<>();

        List<SchedulePlanPreviewResponse.Item> items = new ArrayList<>();
        int conflictCount = 0;
        int skippedCount = 0;
        for (SchedulePlanExpander.Occurrence occurrence : occurrences) {
            boolean skipped = resolved.skipDates().contains(occurrence.lessonDate());
            List<String> messages = occupancy.conflictMessages(occurrence);
            // 禁排与占用同一层判定：命中即算冲突,草案不勾选、提交整批回滚
            messages.addAll(ruleSet.forbiddenMessages(occurrence.lessonDate(), occurrence.weekday(), occurrence.timeSlotId()));
            boolean conflict = !messages.isEmpty();
            items.add(new SchedulePlanPreviewResponse.Item(
                occurrence.weekNo(), occurrence.lessonDate(), occurrence.weekday(), occurrence.timeSlotId(),
                resolved.slotName(occurrence), resolved.startTime(occurrence), resolved.endTime(occurrence),
                conflict, messages, skipped, !conflict && !skipped));
            if (conflict) {
                conflictCount++;
            }
            if (skipped) {
                skippedCount++;
            }
            if (closedDays.containsKey(occurrence.lessonDate())) {
                lessonsOnClosedDay.merge(occurrence.lessonDate(), 1, Integer::sum);
            }
        }
        // 必排只提示"这份计划的每周模式没覆盖到该格子",不拦提交：逐周缺排属完成度问题,归阶段C的完成度视图
        Set<String> plannedCells = resolved.slots().stream()
            .map(slot -> ScheduleRuleSupport.cellKey(slot.weekday(), slot.timeSlotId()))
            .collect(Collectors.toSet());
        List<String> ruleNotices = new ArrayList<>(ruleSet.requiredGapMessages(plannedCells));
        // 撞停排日同样只提示不拦：操作员认定某年国庆照常补课时可以照样提交，不必先删日历记录
        lessonsOnClosedDay.forEach((date, count) ->
            ruleNotices.add(date + " " + closedDays.get(date) + "，本计划有 " + count + " 节课落在这一天"));
        return new SchedulePlanPreviewResponse(items, items.size(), conflictCount, skippedCount, ruleNotices);
    }

    @Override
    @Transactional
    public SchedulePlanCommitResponse commit(SchedulePlanCommitRequest request) {
        SchedulePlanRequest planRequest = request.plan();
        ResolvedPlan resolved = resolve(planRequest);
        assertCommittable(request.planId(), resolved);
        List<SchedulePlanExpander.Occurrence> occurrences = expand(resolved, planRequest);
        Map<String, SchedulePlanExpander.Occurrence> occurrenceByKey = new LinkedHashMap<>();
        occurrences.forEach(occurrence -> occurrenceByKey.putIfAbsent(occupancyKey(occurrence.lessonDate(), occurrence.timeSlotId()), occurrence));

        List<SchedulePlanExpander.Occurrence> selected = new ArrayList<>();
        for (SchedulePlanOccurrence lesson : request.lessons()) {
            SchedulePlanExpander.Occurrence occurrence = occurrenceByKey.get(occupancyKey(lesson.lessonDate(), lesson.timeSlotId()));
            if (occurrence == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "勾选的课次不在计划展开范围内：" + lesson.lessonDate() + " 时段" + lesson.timeSlotId());
            }
            // 预览里跳过日期只作提示、草案仍列出，所以这里要兜住绕过前端禁选直接提交的情况
            if (resolved.skipDates().contains(lesson.lessonDate())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "勾选的课次命中跳过日期：" + lesson.lessonDate());
            }
            if (!selected.contains(occurrence)) {
                selected.add(occurrence);
            }
        }

        Long planId = savePlan(request.planId(), planRequest, resolved);
        // 教师占用没有唯一键可兜，先查后插之前取行锁；计划里所有课次共用同一批教师，整批锁一次即可
        teacherLock.lock(resolved.teacherIds());
        Set<String> existingKeys = loadPlanLessonKeys(planId);
        ScheduleRuleSupport.RuleSet ruleSet = ruleSupport.loadRuleSet(
            planRequest.institutionId(), List.of(planRequest.classId()), resolved.teacherIds(),
            List.of(planRequest.classroomId()));
        Occupancy occupancy = loadOccupancy(planRequest.classId(), planRequest.classroomId(), resolved.teacherIds(),
            planRequest.startDate(), planRequest.endDate(), planId);

        int created = 0;
        int skipped = 0;
        List<String> skipMessages = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (SchedulePlanExpander.Occurrence occurrence : selected) {
            String key = occupancyKey(occurrence.lessonDate(), occurrence.timeSlotId());
            if (existingKeys.contains(key)) {
                skipped++;
                skipMessages.add(occurrence.lessonDate() + " 时段" + occurrence.timeSlotId() + "：该计划已生成过课次，跳过");
                continue;
            }
            List<String> conflicts = occupancy.conflictMessages(occurrence);
            conflicts.addAll(ruleSet.forbiddenMessages(occurrence.lessonDate(), occurrence.weekday(), occurrence.timeSlotId()));
            if (!conflicts.isEmpty()) {
                failures.add(occurrence.lessonDate() + "：" + String.join("；", conflicts));
                continue;
            }
            insertLesson(planRequest, resolved, occurrence, planId);
            created++;
        }
        if (!failures.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "勾选课次存在冲突，本次提交未生效：" + String.join("；", failures));
        }
        return new SchedulePlanCommitResponse(planId, created, skipped, skipMessages);
    }

    @Override
    @Transactional
    public SchedulePlanView updatePlan(Long id, SchedulePlanRequest request) {
        SchedulePlanEntity existing = requirePlan(id);
        ResolvedPlan resolved = resolve(request);
        applyDefinition(existing, request, resolved);
        planMapper.updateById(existing);
        return toView(requirePlan(id));
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        requirePlan(id);
        planMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int cancelPlanLessons(Long id) {
        requirePlan(id);
        List<ScheduleLessonEntity> lessons = lessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getPlanId, id)
            .in(ScheduleLessonEntity::getLessonStatus, OCCUPYING_STATUSES)
            .ge(ScheduleLessonEntity::getLessonDate, LocalDate.now()));
        for (ScheduleLessonEntity lesson : lessons) {
            teacherRelMapper.physicalDeleteByLessonId(lesson.getId());
            lessonMapper.deleteById(lesson.getId());
        }
        return lessons.size();
    }

    private SchedulePlanEntity requirePlan(Long id) {
        SchedulePlanEntity entity = planMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "排课计划不存在：" + id);
        }
        ScheduleInstitutionGuard.assertOwn(entity.getInstitutionId(), SUBJECT);
        CampusScopeGuard.assertCampus(entity.getCampusId(), "排课计划");
        return entity;
    }

    /**
     * 提交排课只允许对启用中的计划生效。
     *
     * <p>停用中的计划不展开在完成度报表里（分母只取 active），所以此时再生成课次，
     * 这些课次在报表上永久隐形，操作员只会看到"计划没落地"。要补排先启用计划。
     */
    private void assertCommittable(Long planId, ResolvedPlan resolved) {
        if (!STATUS_ACTIVE.equals(resolved.status())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "停用状态的计划不能提交排课，请先在「周期排课计划」里启用该计划");
        }
        if (planId != null) {
            SchedulePlanEntity existing = requirePlan(planId);
            if (!STATUS_ACTIVE.equals(existing.getStatus())) {
                // 计划本身没有业务编号，操作员认的是班级，用班级编码指认这条计划
                TeachingClassEntity planClass = classMapper.selectById(existing.getClassId());
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该计划已停用，不能提交排课，请先启用后再补排（班级："
                        + (planClass == null ? "未知" : planClass.getClassCode()) + "）");
            }
        }
    }

    private Long savePlan(Long planId, SchedulePlanRequest request, ResolvedPlan resolved) {
        if (planId == null) {
            SchedulePlanEntity entity = new SchedulePlanEntity();
            applyDefinition(entity, request, resolved);
            entity.setDeleted(Boolean.FALSE);
            planMapper.insert(entity);
            return entity.getId();
        }
        SchedulePlanEntity entity = requirePlan(planId);
        applyDefinition(entity, request, resolved);
        planMapper.updateById(entity);
        return entity.getId();
    }

    private void applyDefinition(SchedulePlanEntity entity, SchedulePlanRequest request, ResolvedPlan resolved) {
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTermId(request.termId());
        entity.setClassId(request.classId());
        entity.setClassroomId(request.classroomId());
        entity.setTeacherIdsJson(writeJson(resolved.teacherIds()));
        entity.setSlotsJson(writeJson(resolved.slots()));
        entity.setWeekFrequency(resolved.weekFrequency());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setSkipDatesJson(resolved.skipDates().isEmpty() ? null : writeJson(new ArrayList<>(resolved.skipDates())));
        entity.setStatus(resolved.status());
        entity.setRemark(request.remark());
    }

    private void insertLesson(SchedulePlanRequest request, ResolvedPlan resolved,
                              SchedulePlanExpander.Occurrence occurrence, Long planId) {
        TimeSlotEntity slot = resolved.slotById().get(occurrence.timeSlotId());
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setInstitutionId(request.institutionId());
        lesson.setCampusId(request.campusId());
        lesson.setTermId(request.termId());
        lesson.setClassId(request.classId());
        lesson.setClassroomId(request.classroomId());
        lesson.setTimeSlotId(occurrence.timeSlotId());
        lesson.setPlanId(planId);
        lesson.setLessonDate(occurrence.lessonDate());
        lesson.setLessonWeekday(occurrence.weekday());
        lesson.setStartTime(slot.getStartTime());
        lesson.setEndTime(slot.getEndTime());
        lesson.setLessonStatus("scheduled");
        lesson.setDeleted(Boolean.FALSE);
        try {
            lessonMapper.insert(lesson);
        } catch (DuplicateKeyException exception) {
            // 上面的占用检查是先查后插，双页签同时提交会一起通过，兜底的唯一约束见 V72 迁移
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该班级或教室在此时段已有课次（并发写入被数据库唯一约束拦下）："
                    + occurrence.lessonDate() + " 时段" + occurrence.timeSlotId() + "，本次提交未生效");
        }
        for (Long teacherId : resolved.teacherIds()) {
            ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
            relation.setLessonId(lesson.getId());
            relation.setTeacherId(teacherId);
            relation.setDeleted(Boolean.FALSE);
            teacherRelMapper.insert(relation);
        }
    }

    /**
     * 幂等查重靠应用层(计划课次的取消是软删,同键撤掉后重排再撤会与已删行撞含 deleted 的唯一键),
     * 冲突语义与单条排课一致：同日期同时段被占用。
     */
    private Set<String> loadPlanLessonKeys(Long planId) {
        return lessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
                .select(ScheduleLessonEntity::getLessonDate, ScheduleLessonEntity::getTimeSlotId)
                .eq(ScheduleLessonEntity::getPlanId, planId)
                .in(ScheduleLessonEntity::getLessonStatus, OCCUPYING_STATUSES))
            .stream()
            .map(lesson -> occupancyKey(lesson.getLessonDate(), lesson.getTimeSlotId()))
            .collect(Collectors.toSet());
    }

    private Occupancy loadOccupancy(Long classId, Long classroomId, List<Long> teacherIds,
                                    LocalDate startDate, LocalDate endDate, Long excludePlanId) {
        Set<String> classKeys = occupiedKeys(new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getClassId, classId), startDate, endDate, excludePlanId);
        Set<String> classroomKeys = occupiedKeys(new LambdaQueryWrapper<ScheduleLessonEntity>()
            .eq(ScheduleLessonEntity::getClassroomId, classroomId), startDate, endDate, excludePlanId);
        Set<String> teacherKeys = new HashSet<>();
        List<Long> occupiedByTeacherLessonIds = teacherRelMapper.selectOccupiedLessonIdsByTeachers(teacherIds, startDate, endDate);
        if (occupiedByTeacherLessonIds != null && !occupiedByTeacherLessonIds.isEmpty()) {
            lessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
                    .select(ScheduleLessonEntity::getLessonDate, ScheduleLessonEntity::getTimeSlotId)
                    .in(ScheduleLessonEntity::getId, occupiedByTeacherLessonIds)
                    .in(ScheduleLessonEntity::getLessonStatus, OCCUPYING_STATUSES))
                .forEach(lesson -> teacherKeys.add(occupancyKey(lesson.getLessonDate(), lesson.getTimeSlotId())));
        }
        return new Occupancy(classKeys, classroomKeys, teacherKeys);
    }

    private Set<String> occupiedKeys(LambdaQueryWrapper<ScheduleLessonEntity> scope, LocalDate startDate,
                                     LocalDate endDate, Long excludePlanId) {
        scope.select(ScheduleLessonEntity::getLessonDate, ScheduleLessonEntity::getTimeSlotId)
            .between(ScheduleLessonEntity::getLessonDate, startDate, endDate)
            .in(ScheduleLessonEntity::getLessonStatus, OCCUPYING_STATUSES);
        if (excludePlanId != null) {
            // 排除本计划自身课次时不能只写 ne：手工课次 plan_id 为 NULL,NULL<>x 在 SQL 里不为真,会被一并滤掉
            scope.and(w -> w.isNull(ScheduleLessonEntity::getPlanId).or().ne(ScheduleLessonEntity::getPlanId, excludePlanId));
        }
        return lessonMapper.selectList(scope).stream()
            .map(lesson -> occupancyKey(lesson.getLessonDate(), lesson.getTimeSlotId()))
            .collect(Collectors.toSet());
    }

    private static String occupancyKey(LocalDate date, Long timeSlotId) {
        return date + "#" + timeSlotId;
    }

    private List<SchedulePlanExpander.Occurrence> expand(ResolvedPlan resolved, SchedulePlanRequest request) {
        long weeks = ChronoUnit.WEEKS.between(
            request.startDate().with(DayOfWeek.MONDAY),
            request.endDate().with(DayOfWeek.MONDAY)) + 1;
        if (weeks > SchedulePlanExpander.MAX_WEEKS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "计划日期范围不能超过" + SchedulePlanExpander.MAX_WEEKS + "周");
        }
        try {
            return SchedulePlanExpander.expand(resolved.slots(), resolved.weekFrequency(),
                request.startDate(), request.endDate());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, e.getMessage());
        }
    }

    private ResolvedPlan resolve(SchedulePlanRequest request) {
        // 预览/提交/更新都先过这一关：请求声明的机构必须就是登录机构，下面的资料归属校验才成立
        ScheduleInstitutionGuard.assertOwn(request.institutionId(), SUBJECT);
        CampusScopeGuard.assertCampus(request.campusId(), "排课计划");
        TeachingClassEntity teachingClass = classMapper.selectById(request.classId());
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + request.classId());
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), request.institutionId())
            || !Objects.equals(teachingClass.getTermId(), request.termId())
            || !Objects.equals(teachingClass.getCampusId(), request.campusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级的学期/校区/机构与计划不一致");
        }
        ClassroomEntity classroom = classroomMapper.selectById(request.classroomId());
        if (classroom == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教室不存在：" + request.classroomId());
        }
        if (!Objects.equals(classroom.getCampusId(), request.campusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "教室校区与计划不一致");
        }
        List<Long> teacherIds = request.teacherIds().stream().filter(Objects::nonNull).distinct().toList();
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
                throw new BusinessException(ResultCode.BAD_REQUEST, "教师的机构/校区与计划不一致");
            }
        }
        List<ScheduleSlotCell> slots = request.slots().stream()
            .filter(slot -> slot != null && slot.weekday() != null && slot.timeSlotId() != null)
            .distinct()
            .toList();
        if (slots.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节次模式不能为空");
        }
        Map<Long, TimeSlotEntity> slotById = new LinkedHashMap<>();
        for (ScheduleSlotCell slot : slots) {
            TimeSlotEntity timeSlot = timeSlotMapper.selectById(slot.timeSlotId());
            if (timeSlot == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "时间段不存在：" + slot.timeSlotId());
            }
            if (!Objects.equals(timeSlot.getInstitutionId(), request.institutionId())
                || (timeSlot.getCampusId() != null && !Objects.equals(timeSlot.getCampusId(), request.campusId()))) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "时间段与计划不匹配：" + slot.timeSlotId());
            }
            if (!Objects.equals(timeSlot.getWeekday(), slot.weekday())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "计划星期与时间段星期不一致：" + timeSlot.getSlotName());
            }
            slotById.put(slot.timeSlotId(), timeSlot);
        }
        String weekFrequency = normalize(request.weekFrequency());
        if (weekFrequency == null || weekFrequency.isBlank()) {
            weekFrequency = SchedulePlanExpander.EVERY_WEEK;
        }
        weekFrequency = weekFrequency.toLowerCase();
        if (!List.of(SchedulePlanExpander.EVERY_WEEK, SchedulePlanExpander.ODD_WEEK, SchedulePlanExpander.EVEN_WEEK)
            .contains(weekFrequency)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "排课频率只能是每周、单周、双周之一");
        }
        String status = normalize(request.status());
        if (status == null || status.isBlank()) {
            status = STATUS_ACTIVE;
        }
        status = status.toLowerCase();
        if (!List.of(STATUS_ACTIVE, STATUS_INACTIVE).contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "计划状态只能是启用或停用");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "计划起始日期不能晚于截止日期");
        }
        Set<LocalDate> skipDates = request.skipDates() == null
            ? Set.of()
            : request.skipDates().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        return new ResolvedPlan(slots, weekFrequency, teacherIds, skipDates, slotById, status);
    }

    private SchedulePlanView toView(SchedulePlanEntity entity) {
        return new SchedulePlanView(
            entity.getId(),
            entity.getInstitutionId(),
            entity.getCampusId(),
            entity.getTermId(),
            entity.getClassId(),
            entity.getClassroomId(),
            readJson(entity.getTeacherIdsJson(), new TypeReference<List<Long>>() {
            }),
            readJson(entity.getSlotsJson(), new TypeReference<List<ScheduleSlotCell>>() {
            }),
            entity.getWeekFrequency(),
            entity.getStartDate(),
            entity.getEndDate(),
            readJson(entity.getSkipDatesJson(), new TypeReference<List<LocalDate>>() {
            }),
            entity.getStatus(),
            entity.getRemark(),
            entity.getCreatedAt()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "排课计划序列化失败");
        }
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "排课计划数据解析失败");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private record ResolvedPlan(List<ScheduleSlotCell> slots, String weekFrequency, List<Long> teacherIds,
                                Set<LocalDate> skipDates, Map<Long, TimeSlotEntity> slotById, String status) {

        String slotName(SchedulePlanExpander.Occurrence occurrence) {
            return slotById.get(occurrence.timeSlotId()).getSlotName();
        }

        LocalTime startTime(SchedulePlanExpander.Occurrence occurrence) {
            return slotById.get(occurrence.timeSlotId()).getStartTime();
        }

        LocalTime endTime(SchedulePlanExpander.Occurrence occurrence) {
            return slotById.get(occurrence.timeSlotId()).getEndTime();
        }
    }

    private record Occupancy(Set<String> classKeys, Set<String> classroomKeys, Set<String> teacherKeys) {

        List<String> conflictMessages(SchedulePlanExpander.Occurrence occurrence) {
            String key = occupancyKey(occurrence.lessonDate(), occurrence.timeSlotId());
            List<String> messages = new ArrayList<>();
            if (classKeys.contains(key)) {
                messages.add("该班级在此日此节次已有排课");
            }
            if (classroomKeys.contains(key)) {
                messages.add("该教室在此日此节次已被占用");
            }
            if (teacherKeys.contains(key)) {
                messages.add("授课教师在此日此节次已被占用");
            }
            return messages;
        }
    }
}
