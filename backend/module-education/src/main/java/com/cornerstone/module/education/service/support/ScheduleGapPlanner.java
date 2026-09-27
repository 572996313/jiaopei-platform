package com.cornerstone.module.education.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.SchedulePlanEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.ScheduleAutoFailure;
import com.cornerstone.module.education.dto.ScheduleAutoPlacement;
import com.cornerstone.module.education.dto.ScheduleAutoStrategy;
import com.cornerstone.module.education.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.SchedulePlanMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.SchoolCalendarService;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 缺口补排的决策核心：把完成度报表算出的"这个班这周还差几节"落成具体某一天某一节。
 *
 * <p>一期只做补排，不改 {@code jp_schedule_plan}：教师与教室沿用该班既有计划，引擎不换人也不换房。
 * 决策全在内存里做——一次取齐窗口内的节次、占用、禁排规则、停排日与教师上限，之后不再回库；
 * 每落一格就更新这几份占用视图，下一个格子对着的是"已经排完这一格"的世界重新算。
 *
 * <p>硬约束与手工排课同一套（占用、禁排、跳过日期、计划区间），差别只在两处：
 * <ul>
 *   <li>教师周/日上限对手工是提示、对引擎是硬约束——机器排的课没人逐条看，越线就是错。</li>
 *   <li>停排日对手工是提示、对引擎是兜底档：有非停排日的位置就不碰它，实在没位置才用并记下提示。</li>
 * </ul>
 */
@Component
public class ScheduleGapPlanner {

    /** 会占住格子的课次状态，与计划展开器同口径（已停课的课次不占位，可以另排补课）。 */
    private static final List<String> OCCUPYING_STATUSES = List.of("scheduled", "rescheduled", "makeup");
    /** 节次里不参与排课的类型：课间是空档，排进课表会让整天的节次表失真。 */
    private static final String SLOT_TYPE_BREAK = "break";
    private static final String PLAN_STATUS_ACTIVE = "active";
    private static final String SLOT_STATUS_ACTIVE = "active";
    /** 停排日格子的罚分：大到只有"实在没位置"时才会被选上。 */
    private static final int CLOSED_DAY_PENALTY = 100;
    /** 落在该班这一周已经排过课的那一天的奖励（少来一趟）。 */
    private static final int SAME_DAY_BONUS = 20;
    /** 贴着该班已有课次的相邻节次的奖励，弱于"同一天"。 */
    private static final int ADJACENT_SLOT_BONUS = 10;
    /** 落位来源：补上计划里没展开出的格子，与计划外空位相对。 */
    private static final String SOURCE_PLAN_GAP = "plan_gap";
    private static final String SOURCE_FREE_CELL = "free_cell";

    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final SchedulePlanMapper planMapper;
    private final ScheduleLessonMapper lessonMapper;
    private final ScheduleTeacherRelMapper teacherRelMapper;
    private final ScheduleRuleSupport ruleSupport;
    private final SchoolCalendarService calendarService;
    private final ObjectMapper objectMapper;

    public ScheduleGapPlanner(TeacherMapper teacherMapper, ClassroomMapper classroomMapper,
                             TimeSlotMapper timeSlotMapper, SchedulePlanMapper planMapper,
                             ScheduleLessonMapper lessonMapper, ScheduleTeacherRelMapper teacherRelMapper,
                             ScheduleRuleSupport ruleSupport, SchoolCalendarService calendarService,
                             ObjectMapper objectMapper) {
        this.teacherMapper = teacherMapper;
        this.classroomMapper = classroomMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.planMapper = planMapper;
        this.lessonMapper = lessonMapper;
        this.teacherRelMapper = teacherRelMapper;
        this.ruleSupport = ruleSupport;
        this.calendarService = calendarService;
        this.objectMapper = objectMapper;
    }

    /** 一次补排的产出：落好的格子 + 说清为什么补不上的明细 + 进引擎的缺口总数。 */
    public record Result(List<ScheduleAutoPlacement> placements, List<ScheduleAutoFailure> failures, int requestedCount) {
    }

    /**
     * 按完成度报表排一遍。报表已经算好每班每周缺几节（含目标口径与计划口径），这里只回答"补在哪"。
     *
     * @param classIds 指定的班级，空表示报表里全部有缺口的班级
     */
    public Result plan(Long institutionId, Long campusId, LocalDate startDate, LocalDate endDate,
                       List<Long> classIds, ScheduleAutoStrategy strategy, ScheduleCompletenessResponse report) {
        LocalDate from = later(startDate, report.weekFrom());
        LocalDate to = earlier(endDate, report.weekTo());
        ScheduleAutoStrategy used = strategy == null ? new ScheduleAutoStrategy(null, null, null) : strategy;
        List<ScheduleAutoFailure> failures = new ArrayList<>();
        List<Row> rows = selectRows(report, classIds, failures);
        if (rows.isEmpty()) {
            return new Result(List.of(), failures, 0);
        }
        World world = loadWorld(institutionId, campusId, rows, from, to);

        List<ScheduleAutoPlacement> placements = new ArrayList<>();
        int requested = 0;
        for (Row row : rows) {
            for (Week week : row.weeks) {
                for (int i = 0; i < week.missing(); i++) {
                    requested++;
                    PlacementResult placed = placeOne(world, used, row, week, from, to);
                    if (placed.placement() == null) {
                        failures.add(new ScheduleAutoFailure(row.classId(), row.className(), week.weekNo(), placed.reason()));
                        break;
                    }
                    placements.add(placed.placement());
                }
            }
        }
        return new Result(placements, failures, requested);
    }

    /** 报表行 → 待处理行：按指定班级筛掉，"整班没法排"的原因在这里就地记掉。 */
    private List<Row> selectRows(ScheduleCompletenessResponse report, List<Long> classIds,
                                 List<ScheduleAutoFailure> failures) {
        List<Long> wanted = classIds == null || classIds.isEmpty() ? List.of() : classIds;
        List<Row> rows = new ArrayList<>();
        for (ScheduleCompletenessResponse.ClassRow classRow : report.classes()) {
            if (!wanted.isEmpty() && !wanted.contains(classRow.classId())) {
                continue;
            }
            List<Week> weeks = weeksOf(report, classRow);
            if (weeks.isEmpty()) {
                continue;
            }
            ClassContext context = classContext(classRow, failures);
            if (context == null) {
                continue;
            }
            rows.add(new Row(classRow.classId(), classRow.className(), context, weeks));
        }
        return rows;
    }

    /** 只留下有缺口的周。一个班可能只缺某一周的 1 节，逐周独立处理，不要把整班摊平成一个总数。 */
    private List<Week> weeksOf(ScheduleCompletenessResponse report, ScheduleCompletenessResponse.ClassRow classRow) {
        List<Week> weeks = new ArrayList<>();
        for (int i = 0; i < classRow.cells().size() && i < report.weeks().size(); i++) {
            ScheduleCompletenessResponse.WeekCell cell = classRow.cells().get(i);
            if (cell.missing() > 0) {
                ScheduleCompletenessResponse.WeekColumn column = report.weeks().get(i);
                weeks.add(new Week(column.index(), column.weekStart(), cell.missing(), cell.gaps()));
            }
        }
        return weeks;
    }

    /**
     * 该班启用中计划给出的教师与教室。一期引擎不换人也不换房，所以要求各计划口径一致；
     * 不一致时不猜，把班退回给操作员——多计划且教师不同的班，补哪一批都可能排错。
     */
    private ClassContext classContext(ScheduleCompletenessResponse.ClassRow classRow,
                                      List<ScheduleAutoFailure> failures) {
        List<SchedulePlanEntity> plans = planMapper.selectList(new LambdaQueryWrapper<SchedulePlanEntity>()
            .eq(SchedulePlanEntity::getClassId, classRow.classId())
            .eq(SchedulePlanEntity::getStatus, PLAN_STATUS_ACTIVE));
        if (plans.isEmpty()) {
            failures.add(new ScheduleAutoFailure(classRow.classId(), classRow.className(), null,
                "没有启用中的排课计划，引擎无法判定教师与教室"));
            return null;
        }
        Set<Long> rooms = new LinkedHashSet<>();
        Set<Long> teachers = new LinkedHashSet<>();
        LocalDate planFrom = null;
        LocalDate planTo = null;
        for (SchedulePlanEntity plan : plans) {
            if (plan.getClassroomId() != null) {
                rooms.add(plan.getClassroomId());
            }
            teachers.addAll(readIds(plan.getTeacherIdsJson()));
            planFrom = earlier(planFrom, plan.getStartDate());
            planTo = later(planTo, plan.getEndDate());
        }
        if (rooms.size() != 1) {
            failures.add(new ScheduleAutoFailure(classRow.classId(), classRow.className(), null,
                "该班的多个启用中计划用了不同教室，请先确认教室后再补排"));
            return null;
        }
        if (teachers.isEmpty()) {
            failures.add(new ScheduleAutoFailure(classRow.classId(), classRow.className(), null,
                "该班的启用中计划没有授课教师"));
            return null;
        }
        return new ClassContext(List.copyOf(rooms), List.copyOf(teachers), planFrom, planTo);
    }

    /** 一次取齐整批要用的节次、占用、规则、停排日与教师上限。 */
    private World loadWorld(Long institutionId, Long campusId, List<Row> rows, LocalDate from, LocalDate to) {
        List<TimeSlotEntity> slots = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlotEntity>()
                .eq(TimeSlotEntity::getInstitutionId, institutionId)
                .and(w -> w.eq(TimeSlotEntity::getCampusId, campusId).or().isNull(TimeSlotEntity::getCampusId))
                .eq(TimeSlotEntity::getStatus, SLOT_STATUS_ACTIVE)
                .orderByAsc(TimeSlotEntity::getStartTime))
            .stream()
            .filter(slot -> !SLOT_TYPE_BREAK.equalsIgnoreCase(slot.getSlotType()))
            .toList();
        Map<Integer, List<TimeSlotEntity>> slotsByWeekday = new LinkedHashMap<>();
        for (TimeSlotEntity slot : slots) {
            if (slot.getWeekday() != null) {
                slotsByWeekday.computeIfAbsent(slot.getWeekday(), key -> new ArrayList<>()).add(slot);
            }
        }

        List<Long> classIds = rows.stream().map(row -> row.classId()).distinct().toList();
        Set<Long> classroomIds = new HashSet<>();
        Set<Long> teacherIds = new HashSet<>();
        for (Row row : rows) {
            classroomIds.addAll(row.context().classroomIds());
            teacherIds.addAll(row.context().teacherIds());
        }

        Map<String, Set<String>> classKeys = new HashMap<>();
        Map<String, Set<String>> roomKeys = new HashMap<>();
        Map<String, Set<String>> teacherKeys = new HashMap<>();
        Map<Long, Set<LocalDate>> classDays = new HashMap<>();
        Map<String, Integer> classDailyCount = new HashMap<>();
        Map<String, Integer> teacherDailyCount = new HashMap<>();
        Map<String, Integer> teacherWeeklyCount = new HashMap<>();

        lessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
                .in(ScheduleLessonEntity::getClassId, classIds)
                .between(ScheduleLessonEntity::getLessonDate, from, to)
                .in(ScheduleLessonEntity::getLessonStatus, OCCUPYING_STATUSES))
            .forEach(lesson -> {
                classKeys.computeIfAbsent(lesson.getClassId() + "#" + lesson.getLessonDate(), key -> new HashSet<>())
                    .add(cellKey(lesson.getTimeSlotId()));
                classDays.computeIfAbsent(lesson.getClassId(), key -> new HashSet<>()).add(lesson.getLessonDate());
                classDailyCount.merge(lesson.getClassId() + "#" + lesson.getLessonDate(), 1, Integer::sum);
            });
        if (!classroomIds.isEmpty()) {
            lessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
                    .in(ScheduleLessonEntity::getClassroomId, classroomIds)
                    .between(ScheduleLessonEntity::getLessonDate, from, to)
                    .in(ScheduleLessonEntity::getLessonStatus, OCCUPYING_STATUSES))
                .forEach(lesson -> roomKeys
                    .computeIfAbsent(lesson.getClassroomId() + "#" + lesson.getLessonDate(), key -> new HashSet<>())
                    .add(cellKey(lesson.getTimeSlotId())));
        }
        loadTeacherOccupancy(teacherIds, from, to, teacherKeys, teacherDailyCount, teacherWeeklyCount);

        Map<Long, TeacherEntity> teachers = teacherIds.isEmpty() ? Map.of() : mapById(
            teacherMapper.selectBatchIds(new ArrayList<>(teacherIds)), TeacherEntity::getId);
        Map<Long, ClassroomEntity> classrooms = classroomIds.isEmpty() ? Map.of() : mapById(
            classroomMapper.selectBatchIds(new ArrayList<>(classroomIds)), ClassroomEntity::getId);
        Map<LocalDate, String> closedDays = calendarService.closedDays(institutionId, campusId, from, to);
        ScheduleRuleSupport.RuleSet rules = ruleSupport.loadRuleSet(institutionId, classIds,
            new ArrayList<>(teacherIds), new ArrayList<>(classroomIds));
        return new World(slotsByWeekday, classKeys, roomKeys, teacherKeys, classDays, classDailyCount,
            teacherDailyCount, teacherWeeklyCount, teachers, classrooms, closedDays, rules);
    }

    /**
     * 教师占用必须按教师分桶：甲班的老师空不空，跟乙班的老师无关。
     * 用一个全局"日期#节次"集合会让任一班占用某格就把所有班的位置判死，缺口永远补不满。
     */
    private void loadTeacherOccupancy(Set<Long> teacherIds, LocalDate from, LocalDate to,
                                      Map<String, Set<String>> teacherKeys,
                                      Map<String, Integer> teacherDailyCount,
                                      Map<String, Integer> teacherWeeklyCount) {
        if (teacherIds.isEmpty()) {
            return;
        }
        List<Long> lessonIds = teacherRelMapper.selectOccupiedLessonIdsByTeachers(new ArrayList<>(teacherIds), from, to);
        if (lessonIds == null || lessonIds.isEmpty()) {
            return;
        }
        List<ScheduleLessonEntity> lessons = lessonMapper.selectList(new LambdaQueryWrapper<ScheduleLessonEntity>()
            .in(ScheduleLessonEntity::getId, lessonIds)
            .in(ScheduleLessonEntity::getLessonStatus, OCCUPYING_STATUSES));
        Map<Long, List<Long>> teachersByLesson = new HashMap<>();
        teacherRelMapper.selectList(new LambdaQueryWrapper<ScheduleTeacherRelEntity>()
                .in(ScheduleTeacherRelEntity::getLessonId, lessonIds))
            .forEach(relation -> teachersByLesson
                .computeIfAbsent(relation.getLessonId(), key -> new ArrayList<>())
                .add(relation.getTeacherId()));
        for (ScheduleLessonEntity lesson : lessons) {
            for (Long teacherId : teachersByLesson.getOrDefault(lesson.getId(), List.of())) {
                teacherKeys.computeIfAbsent(teacherId + "#" + lesson.getLessonDate(), key -> new HashSet<>())
                    .add(cellKey(lesson.getTimeSlotId()));
                teacherDailyCount.merge(teacherId + "#" + lesson.getLessonDate(), 1, Integer::sum);
                teacherWeeklyCount.merge(teacherId + "#" + weekStart(lesson.getLessonDate()), 1, Integer::sum);
            }
        }
    }

    /** 先填计划里没展开出的格子（补上后计划与课表重新对齐），填不上再从本周空位里挑最优的一格。 */
    private PlacementResult placeOne(World world, ScheduleAutoStrategy strategy, Row row, Week week,
                                     LocalDate from, LocalDate to) {
        for (ScheduleCompletenessResponse.Gap gap : week.gaps()) {
            if (gap.lessonDate() == null || gap.weekday() == null || gap.timeSlotId() == null
                || gap.lessonDate().isBefore(from) || gap.lessonDate().isAfter(to)) {
                continue;
            }
            TimeSlotEntity slot = slotOf(world, gap.weekday(), gap.timeSlotId());
            if (slot == null) {
                // 节次已经停用或不属于本校区：补它等于把课排进课表上不存在的一格
                continue;
            }
            Candidate candidate = new Candidate(gap.lessonDate(), gap.weekday(), slot.getId(), slot.getSlotName());
            if (!isAvailable(world, strategy, row, candidate)) {
                continue;
            }
            return commit(world, row, candidate, slot, SOURCE_PLAN_GAP);
        }
        Candidate best = null;
        TimeSlotEntity bestSlot = null;
        int bestScore = 0;
        for (LocalDate day = week.weekStart(); !day.isAfter(week.weekStart().plusDays(6)); day = day.plusDays(1)) {
            for (TimeSlotEntity slot : world.slotsByWeekday.getOrDefault(day.getDayOfWeek().getValue(), List.of())) {
                Candidate candidate = new Candidate(day, slot.getWeekday(), slot.getId(), slot.getSlotName());
                if (!isAvailable(world, strategy, row, candidate)) {
                    continue;
                }
                int score = score(world, strategy, row, candidate);
                if (best == null || score < bestScore) {
                    best = candidate;
                    bestSlot = slot;
                    bestScore = score;
                }
            }
        }
        if (best == null) {
            return new PlacementResult(null, noPlaceReason(world, strategy, row, week));
        }
        return commit(world, row, best, bestSlot, SOURCE_FREE_CELL);
    }

    /** 一个格子能不能落：计划区间、占用、禁排、教师上限、该班当日上限，五条全过才算可用。 */
    private boolean isAvailable(World world, ScheduleAutoStrategy strategy, Row row, Candidate candidate) {
        if (candidate.date().isBefore(LocalDate.now()) || row.context().outOfPlanWindow(candidate.date())) {
            return false;
        }
        String day = candidate.date().toString();
        String slot = cellKey(candidate.timeSlotId());
        if (taken(world.classKeys, row.classId() + "#" + day, slot)
            || taken(world.teacherKeys, teacherKeysOf(row, day), slot)) {
            return false;
        }
        for (Long roomId : row.context().classroomIds()) {
            if (taken(world.roomKeys, roomId + "#" + day, slot)) {
                return false;
            }
        }
        if (blockedByRule(world, row, candidate) || blockedByTeacherCap(world, row, candidate)) {
            return false;
        }
        return !blockedByDailyCap(strategy, world, row, candidate.date());
    }

    private boolean taken(Map<String, Set<String>> keys, List<String> subjects, String slot) {
        return subjects.stream().anyMatch(subject -> taken(keys, subject, slot));
    }

    private List<String> teacherKeysOf(Row row, String day) {
        return row.context().teacherIds().stream().map(teacherId -> teacherId + "#" + day).toList();
    }

    private boolean taken(Map<String, Set<String>> keys, String subject, String slot) {
        return keys.getOrDefault(subject, Set.of()).contains(slot);
    }

    private boolean blockedByRule(World world, Row row, Candidate candidate) {
        return !ruleSetFor(world, row).forbiddenMessages(candidate.date(), candidate.weekday(),
            candidate.timeSlotId()).isEmpty();
    }

    private ScheduleRuleSupport.RuleSet ruleSetFor(World world, Row row) {
        return world.rules.scopedTo(row.classId(), row.context().teacherIds(), row.context().classroomIds().get(0));
    }

    /** 教师周/日上限对引擎是硬约束：手工排课只提示，是因为屏幕前有人在判断该不该照排。 */
    private boolean blockedByTeacherCap(World world, Row row, Candidate candidate) {
        for (Long teacherId : row.context().teacherIds()) {
            TeacherEntity teacher = world.teachers.get(teacherId);
            if (teacher == null) {
                continue;
            }
            if (reaches(teacher.getMaxDailyLessons(),
                world.teacherDailyCount.get(teacherId + "#" + candidate.date()))) {
                return true;
            }
            if (reaches(teacher.getMaxWeeklyLessons(),
                world.teacherWeeklyCount.get(teacherId + "#" + weekStart(candidate.date())))) {
                return true;
            }
        }
        return false;
    }

    private boolean blockedByDailyCap(ScheduleAutoStrategy strategy, World world, Row row, LocalDate day) {
        Integer cap = strategy.maxDailyPerClassOrNull();
        if (cap == null) {
            return false;
        }
        return world.classDailyCount.getOrDefault(row.classId() + "#" + day, 0) >= cap;
    }

    private boolean reaches(Integer limit, Integer current) {
        if (limit == null || limit <= 0) {
            return false;
        }
        return (current == null ? 0 : current) >= limit;
    }

    /** 分数越小越优先：先躲开停排日，再贴该班已有的日子与相邻节次，同分按节次表先后。 */
    private int score(World world, ScheduleAutoStrategy strategy, Row row, Candidate candidate) {
        int score = 0;
        if (world.closedDays.containsKey(candidate.date())) {
            score += CLOSED_DAY_PENALTY;
        }
        if (strategy.concentrateClassDaysOrDefault()
            && world.classDays.getOrDefault(row.classId(), Set.of()).contains(candidate.date())) {
            score -= SAME_DAY_BONUS;
        }
        if (strategy.preferAdjacentSlotOrDefault() && hasAdjacentClassLesson(world, row, candidate)) {
            score -= ADJACENT_SLOT_BONUS;
        }
        return score;
    }

    /** 该班在这一天是否有课落在相邻节次（按节次表的前后，不按时间差——节次表就是操作员认定的顺序）。 */
    private boolean hasAdjacentClassLesson(World world, Row row, Candidate candidate) {
        List<TimeSlotEntity> daySlots = world.slotsByWeekday.getOrDefault(candidate.weekday(), List.of());
        int index = -1;
        for (int i = 0; i < daySlots.size(); i++) {
            if (daySlots.get(i).getId().equals(candidate.timeSlotId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        Set<String> taken = world.classKeys.getOrDefault(row.classId() + "#" + candidate.date(), Set.of());
        for (int i = index - 1; i <= index + 1; i++) {
            if (i == index || i < 0 || i >= daySlots.size()) {
                continue;
            }
            if (taken.contains(cellKey(daySlots.get(i).getId()))) {
                return true;
            }
        }
        return false;
    }

    private PlacementResult commit(World world, Row row, Candidate candidate, TimeSlotEntity slot, String source) {
        ScheduleAutoPlacement placement = toPlacement(world, row, candidate, slot, source);
        world.commit(row, candidate);
        return new PlacementResult(placement, null);
    }

    private ScheduleAutoPlacement toPlacement(World world, Row row, Candidate candidate, TimeSlotEntity slot,
                                              String source) {
        Long classroomId = row.context().classroomIds().get(0);
        ClassroomEntity classroom = world.classrooms.get(classroomId);
        List<String> names = new ArrayList<>();
        for (Long teacherId : row.context().teacherIds()) {
            TeacherEntity teacher = world.teachers.get(teacherId);
            names.add(teacher == null ? String.valueOf(teacherId) : teacher.getTeacherName());
        }
        return new ScheduleAutoPlacement(row.classId(), row.className(), row.weekNoOf(candidate.date()),
            candidate.date(), candidate.weekday(), candidate.timeSlotId(), candidate.slotName(),
            slot.getStartTime(), slot.getEndTime(),
            classroomId, classroom == null ? null : classroom.getClassroomName(),
            row.context().teacherIds(), String.join("、", names), source,
            world.closedDays.get(candidate.date()));
    }

    private TimeSlotEntity slotOf(World world, Integer weekday, Long timeSlotId) {
        for (TimeSlotEntity slot : world.slotsByWeekday.getOrDefault(weekday, List.of())) {
            if (slot.getId().equals(timeSlotId)) {
                return slot;
            }
        }
        return null;
    }

    /** 一节都没补上时，给一句操作员照着就能去改配置的话，而不是"资源不足"。 */
    private String noPlaceReason(World world, ScheduleAutoStrategy strategy, Row row, Week week) {
        for (Long teacherId : row.context().teacherIds()) {
            TeacherEntity teacher = world.teachers.get(teacherId);
            if (teacher != null && reaches(teacher.getMaxWeeklyLessons(),
                world.teacherWeeklyCount.get(teacherId + "#" + week.weekStart()))) {
                return "授课教师「" + teacher.getTeacherName() + "」本周已达 " + teacher.getMaxWeeklyLessons()
                    + " 节上限，这一周没有剩余位置";
            }
        }
        Integer cap = strategy.maxDailyPerClassOrNull();
        if (cap != null) {
            return "这一周的空位都被规则挡住或已达每日 " + cap + " 节上限（禁排规则、班级计划区间、每日上限）";
        }
        return "这一周该校区已经没有没被占用的节次（或都被禁排规则挡住）";
    }

    private List<Long> readIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception exception) {
            // 计划里的教师列表读不出来的唯一现实场景是历史脏数据；读不出来就当没有，让该班落到"没有授课教师"的失败原因上
            return List.of();
        }
    }

    private static String cellKey(Long timeSlotId) {
        return String.valueOf(timeSlotId);
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private static LocalDate later(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        return right != null && right.isAfter(left) ? right : left;
    }

    private static LocalDate earlier(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        return right != null && right.isBefore(left) ? right : left;
    }

    private static <T> Map<Long, T> mapById(List<T> list, java.util.function.Function<T, Long> idGetter) {
        Map<Long, T> map = new LinkedHashMap<>();
        for (T item : list) {
            map.put(idGetter.apply(item), item);
        }
        return map;
    }

    /** 一个班级在这次补排里的待处理集合。 */
    private record Row(Long classId, String className, ClassContext context, List<Week> weeks) {

        int weekNoOf(LocalDate date) {
            for (Week week : weeks) {
                if (!date.isBefore(week.weekStart()) && date.isBefore(week.weekStart().plusDays(7))) {
                    return week.weekNo();
                }
            }
            return 1;
        }
    }

    /** 班级在这次补排里可用的教室、教师与计划区间。 */
    private record ClassContext(List<Long> classroomIds, List<Long> teacherIds, LocalDate planFrom, LocalDate planTo) {

        boolean outOfPlanWindow(LocalDate day) {
            if (planFrom != null && day.isBefore(planFrom)) {
                return true;
            }
            return planTo != null && day.isAfter(planTo);
        }
    }

    /** 报表给出的一周待补数据。 */
    private record Week(int weekNo, LocalDate weekStart, int missing, List<ScheduleCompletenessResponse.Gap> gaps) {
    }

    /** 一个候选格子：日期 + 星期 + 节次。 */
    private record Candidate(LocalDate date, Integer weekday, Long timeSlotId, String slotName) {
    }

    /** 一次落位的结论：要么给出格子，要么给出原因。 */
    private record PlacementResult(ScheduleAutoPlacement placement, String reason) {
    }

    /**
     * 一次补排共用的内存视图。占用与计数的几个 Map 会随落位增长，所以故意不做成不可变：
     * 键里都带日期，命中一格只动那一天，重算下一个格子时读到的就是"这一格已排完"的世界。
     */
    private static final class World {
        private final Map<Integer, List<TimeSlotEntity>> slotsByWeekday;
        private final Map<String, Set<String>> classKeys;
        private final Map<String, Set<String>> roomKeys;
        private final Map<String, Set<String>> teacherKeys;
        private final Map<Long, Set<LocalDate>> classDays;
        private final Map<String, Integer> classDailyCount;
        private final Map<String, Integer> teacherDailyCount;
        private final Map<String, Integer> teacherWeeklyCount;
        private final Map<Long, TeacherEntity> teachers;
        private final Map<Long, ClassroomEntity> classrooms;
        private final Map<LocalDate, String> closedDays;
        private final ScheduleRuleSupport.RuleSet rules;

        private World(Map<Integer, List<TimeSlotEntity>> slotsByWeekday, Map<String, Set<String>> classKeys,
                      Map<String, Set<String>> roomKeys, Map<String, Set<String>> teacherKeys,
                      Map<Long, Set<LocalDate>> classDays, Map<String, Integer> classDailyCount,
                      Map<String, Integer> teacherDailyCount, Map<String, Integer> teacherWeeklyCount,
                      Map<Long, TeacherEntity> teachers, Map<Long, ClassroomEntity> classrooms,
                      Map<LocalDate, String> closedDays, ScheduleRuleSupport.RuleSet rules) {
            this.slotsByWeekday = slotsByWeekday;
            this.classKeys = classKeys;
            this.roomKeys = roomKeys;
            this.teacherKeys = teacherKeys;
            this.classDays = classDays;
            this.classDailyCount = classDailyCount;
            this.teacherDailyCount = teacherDailyCount;
            this.teacherWeeklyCount = teacherWeeklyCount;
            this.teachers = teachers;
            this.classrooms = classrooms;
            this.closedDays = closedDays;
            this.rules = rules;
        }

        private void commit(Row row, Candidate candidate) {
            classKeys.computeIfAbsent(row.classId() + "#" + candidate.date(), key -> new HashSet<>())
                .add(cellKey(candidate.timeSlotId()));
            for (Long roomId : row.context().classroomIds()) {
                roomKeys.computeIfAbsent(roomId + "#" + candidate.date(), key -> new HashSet<>())
                    .add(cellKey(candidate.timeSlotId()));
            }
            for (Long teacherId : row.context().teacherIds()) {
                teacherKeys.computeIfAbsent(teacherId + "#" + candidate.date(), key -> new HashSet<>())
                    .add(cellKey(candidate.timeSlotId()));
                teacherDailyCount.merge(teacherId + "#" + candidate.date(), 1, Integer::sum);
                teacherWeeklyCount.merge(teacherId + "#" + weekStart(candidate.date()), 1, Integer::sum);
            }
            classDays.computeIfAbsent(row.classId(), key -> new HashSet<>()).add(candidate.date());
            classDailyCount.merge(row.classId() + "#" + candidate.date(), 1, Integer::sum);
        }
    }
}
