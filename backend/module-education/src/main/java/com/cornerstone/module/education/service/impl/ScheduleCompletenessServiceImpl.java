package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.SchedulePlanEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ScheduleCompletenessQuery;
import com.cornerstone.module.education.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.dto.ScheduleSlotCell;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.SchedulePlanMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ScheduleCompletenessService;
import com.cornerstone.module.education.service.support.SchedulePlanExpander;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 排课完成度汇总：把「启用中的排课计划展开」当分母，把「已生成课次」当分子，按班级×自然周对账。
 *
 * <p>口径说明（页面上也要写清，否则数字会被读错）：
 * <ul>
 *   <li>分母有两套：班级设了「每周应上节数」就按目标数（{@code weekly_target}），没设则回落到该班
 *       启用中的排课计划展开（{@code plan_expand}）。两种都只算被计划区间覆盖的周，跳过日期不计，
 *       没建计划的班级不出现在报表里。</li>
 *   <li>匹配按 {@code 日期#时段} 键而不是 planId：落在计划空格子上的手工课次就是把那一格补上了，
 *       会抵扣缺口；只有落在计划键之外的（改期调课、额外加课、补课另排的那一格）才算「计划外」。</li>
 *   <li>应排/已生成/已完成/已停课都按<b>格子</b>计数，同一格有多行只算一格，
 *       所以恒有 已完成 &le; 已生成 &le; 应排；「计划外」按<b>行数</b>计，因为多出来的是真实课时。</li>
 *   <li>停课(cancelled)与结课(completed)的课次仍占在它那一格里，单列显示；
 *       但两者都会让出排课占用（见 V72 的占用防重键），所以同一格可能再排进一节课，
 *       此时完成度只认这一格已生成，重复排课要到「执行与课表」看撞课标记。</li>
 * </ul>
 */
@Service
public class ScheduleCompletenessServiceImpl implements ScheduleCompletenessService {

    /** 分母口径：班级设了每周应上节数 */
    private static final String SOURCE_WEEKLY_TARGET = "weekly_target";
    /** 分母口径：没设目标，回落到该班启用中的排课计划展开 */
    private static final String SOURCE_PLAN_EXPAND = "plan_expand";

    private static final String STATUS_ACTIVE = "active";
    private static final String LESSON_COMPLETED = "completed";
    private static final String LESSON_CANCELLED = "cancelled";
    /** 统计近期节奏的周数：只往回看四周，避开寒暑假对结课日推算的稀释 */
    private static final int RECENT_RATE_WEEKS = 4;
    /** 统计区间上限(周),与计划展开器的 120 周上限不冲突,纯粹防止一屏放不下 */
    private static final int MAX_WEEKS = 26;
    /** 未指定任何日期与学期时的默认窗口(周) */
    private static final int DEFAULT_WEEKS = 12;

    private final SchedulePlanMapper planMapper;
    private final ScheduleLessonMapper lessonMapper;
    private final TeachingClassMapper classMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TermMapper termMapper;
    private final ObjectMapper objectMapper;

    public ScheduleCompletenessServiceImpl(SchedulePlanMapper planMapper, ScheduleLessonMapper lessonMapper,
                                           TeachingClassMapper classMapper, TimeSlotMapper timeSlotMapper,
                                           TermMapper termMapper, ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.lessonMapper = lessonMapper;
        this.classMapper = classMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.termMapper = termMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScheduleCompletenessResponse report(Long operatorInstitutionId, ScheduleCompletenessQuery query) {
        Long institutionId = requireInstitution(operatorInstitutionId, query);
        LocalDate[] range = resolveRange(query);
        LocalDate weekFrom = range[0];
        LocalDate weekTo = range[1];
        int weekCount = (int) ChronoUnit.WEEKS.between(weekFrom, weekTo) + 1;
        List<ScheduleCompletenessResponse.WeekColumn> weeks = buildWeeks(weekFrom, weekCount);

        if (query.getCampusId() != null) {
            CampusScopeGuard.assertCampus(query.getCampusId(), "排课完成度");
        }
        LambdaQueryWrapper<SchedulePlanEntity> planWrapper = new LambdaQueryWrapper<SchedulePlanEntity>()
            .eq(SchedulePlanEntity::getInstitutionId, institutionId)
            .eq(SchedulePlanEntity::getStatus, STATUS_ACTIVE)
            .eq(query.getCampusId() != null, SchedulePlanEntity::getCampusId, query.getCampusId())
            .eq(query.getTermId() != null, SchedulePlanEntity::getTermId, query.getTermId())
            .eq(query.getClassId() != null, SchedulePlanEntity::getClassId, query.getClassId())
            .ge(SchedulePlanEntity::getEndDate, weekFrom)
            .le(SchedulePlanEntity::getStartDate, weekTo)
            .orderByAsc(SchedulePlanEntity::getStartDate);
        // 报表按计划校区收窄，受限角色看不到其他校区的完成度
        CampusScopeGuard.apply(planWrapper, SchedulePlanEntity::getCampusId);
        List<SchedulePlanEntity> plans = planMapper.selectList(planWrapper);
        if (plans.isEmpty()) {
            return new ScheduleCompletenessResponse(weekFrom, weekTo, weeks, List.of(),
                new ScheduleCompletenessResponse.Summary(0, 0, 0, 0, 0, 0, 0, 0));
        }

        List<Long> classIds = plans.stream().map(SchedulePlanEntity::getClassId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, TeachingClassEntity> classById = classMapper.selectList(
                new LambdaQueryWrapper<TeachingClassEntity>().in(TeachingClassEntity::getId, classIds))
            .stream().collect(Collectors.toMap(TeachingClassEntity::getId, item -> item));

        // 一次取齐整段区间的课次与节次名称，后面全在内存里比对
        Map<Long, List<ScheduleLessonEntity>> lessonsByClass = lessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLessonEntity>()
                    .eq(ScheduleLessonEntity::getInstitutionId, institutionId)
                    .in(ScheduleLessonEntity::getClassId, classIds)
                    .ge(ScheduleLessonEntity::getLessonDate, weekFrom)
                    .le(ScheduleLessonEntity::getLessonDate, weekTo))
            .stream().collect(Collectors.groupingBy(ScheduleLessonEntity::getClassId));
        Map<Long, String> slotNames = slotNames(plans);
        Map<Long, Progress> progressByClass = progressFor(institutionId, classIds, classById, LocalDate.now());

        List<ScheduleCompletenessResponse.ClassRow> rows = new ArrayList<>();
        for (Long classId : classIds) {
            rows.add(buildRow(classId, plansFor(classId, plans), lessonsByClass.getOrDefault(classId, List.of()),
                classById.get(classId), slotNames, progressByClass.get(classId), weekFrom, weekCount));
        }
        rows.sort(Comparator.comparing((ScheduleCompletenessResponse.ClassRow row) ->
                Objects.toString(row.className(), ""))
            .thenComparing(row -> Objects.toString(row.classId(), "")));

        int expected = rows.stream().mapToInt(row -> row.summary().expected()).sum();
        int generated = rows.stream().mapToInt(row -> row.summary().generated()).sum();
        int completed = rows.stream().mapToInt(row -> row.summary().completed()).sum();
        int missing = rows.stream().mapToInt(row -> row.summary().missing()).sum();
        int extra = rows.stream().mapToInt(row -> row.summary().extra()).sum();
        return new ScheduleCompletenessResponse(weekFrom, weekTo, weeks, rows,
            new ScheduleCompletenessResponse.Summary(rows.size(), expected, generated, completed, missing, extra,
                percent(generated, expected), percent(completed, expected)));
    }

    // ------------------------------------------------------------------ 区间与权限

    private Long requireInstitution(Long operatorInstitutionId, ScheduleCompletenessQuery query) {
        if (operatorInstitutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前登录账号未绑定机构，无法统计排课完成度");
        }
        if (query.getInstitutionId() != null && !Objects.equals(query.getInstitutionId(), operatorInstitutionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他机构的排课完成度");
        }
        return operatorInstitutionId;
    }

    /**
     * 解析统计区间并对齐到整周，返回 [起始周一, 结束周日]。
     * 日期参数优先，其次学期起止，都没有则取本周起 {@value #DEFAULT_WEEKS} 周。
     */
    private LocalDate[] resolveRange(ScheduleCompletenessQuery query) {
        TermEntity term = query.getTermId() == null ? null : termMapper.selectById(query.getTermId());
        LocalDate from = firstNonNull(query.getDateFrom(), term == null ? null : term.getStartDate());
        LocalDate to = firstNonNull(query.getDateTo(), term == null ? null : term.getEndDate());
        if (from == null && to == null) {
            from = LocalDate.now().with(DayOfWeek.MONDAY);
            to = from.plusWeeks(DEFAULT_WEEKS - 1L).plusDays(6);
        } else if (from == null) {
            // 只给一端就按那一周算，不让区间悄悄退化成"从有数据以来"
            from = to;
        } else if (to == null) {
            to = from;
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "统计起始日期不能晚于截止日期");
        }
        LocalDate weekFrom = from.with(DayOfWeek.MONDAY);
        LocalDate weekTo = to.with(DayOfWeek.SUNDAY);
        if (ChronoUnit.WEEKS.between(weekFrom, weekTo) + 1 > MAX_WEEKS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "统计区间不能超过 " + MAX_WEEKS + " 周");
        }
        return new LocalDate[] {weekFrom, weekTo};
    }

    private static LocalDate firstNonNull(LocalDate primary, LocalDate fallback) {
        return primary != null ? primary : fallback;
    }

    private static List<ScheduleCompletenessResponse.WeekColumn> buildWeeks(LocalDate weekFrom, int weekCount) {
        List<ScheduleCompletenessResponse.WeekColumn> weeks = new ArrayList<>(weekCount);
        for (int index = 1; index <= weekCount; index++) {
            LocalDate monday = weekFrom.plusWeeks(index - 1L);
            weeks.add(new ScheduleCompletenessResponse.WeekColumn(index, monday, monday.plusDays(6)));
        }
        return weeks;
    }

    // ------------------------------------------------------------------ 单行汇总

    private ScheduleCompletenessResponse.ClassRow buildRow(Long classId, List<SchedulePlanEntity> classPlans,
                                                           List<ScheduleLessonEntity> lessons,
                                                           TeachingClassEntity clazz, Map<Long, String> slotNames,
                                                           Progress progress,
                                                           LocalDate weekFrom, int weekCount) {
        Integer target = clazz == null ? null : clazz.getWeeklyLessons();
        boolean byTarget = target != null && target > 0;
        // 每个下标先放"这一周计划要上哪些格子"，再放"实际有哪些课次落在这一周"
        List<SchedulePlanExpander.Occurrence> expected = new ArrayList<>();
        Set<LocalDate> coveredWeeks = new LinkedHashSet<>();
        for (SchedulePlanEntity plan : classPlans) {
            List<ScheduleSlotCell> slots = readJson(plan.getSlotsJson(), new TypeReference<List<ScheduleSlotCell>>() {
            });
            Set<LocalDate> skipDates = new LinkedHashSet<>(
                readJson(plan.getSkipDatesJson(), new TypeReference<List<LocalDate>>() {
                }));
            for (SchedulePlanExpander.Occurrence occurrence
                : SchedulePlanExpander.expand(slots, plan.getWeekFrequency(), plan.getStartDate(), plan.getEndDate())) {
                if (!skipDates.contains(occurrence.lessonDate())) {
                    expected.add(occurrence);
                }
            }
            LocalDate firstWeek = plan.getStartDate().with(DayOfWeek.MONDAY);
            LocalDate lastWeek = plan.getEndDate().with(DayOfWeek.MONDAY);
            for (int index = 0; index < weekCount; index++) {
                LocalDate monday = weekFrom.plusWeeks(index);
                if (!monday.isBefore(firstWeek) && !monday.isAfter(lastWeek)) {
                    coveredWeeks.add(monday);
                }
            }
        }
        Map<LocalDate, List<ScheduleLessonEntity>> lessonsByWeek = lessons.stream()
            .filter(lesson -> lesson.getLessonDate() != null)
            .collect(Collectors.groupingBy(lesson -> lesson.getLessonDate().with(DayOfWeek.MONDAY)));

        List<ScheduleCompletenessResponse.WeekCell> cells = new ArrayList<>(weekCount);
        int totalExpected = 0;
        int totalGenerated = 0;
        int totalCompleted = 0;
        int totalMissing = 0;
        int totalExtra = 0;
        for (int index = 0; index < weekCount; index++) {
            LocalDate monday = weekFrom.plusWeeks(index);
            boolean covered = coveredWeeks.contains(monday);
            LocalDate sunday = monday.plusDays(6);
            Set<String> expectedKeys = new LinkedHashSet<>();
            List<SchedulePlanExpander.Occurrence> weekOccurrences = new ArrayList<>();
            for (SchedulePlanExpander.Occurrence occurrence : expected) {
                if (occurrence.lessonDate().isBefore(monday) || occurrence.lessonDate().isAfter(sunday)) {
                    continue;
                }
                weekOccurrences.add(occurrence);
                expectedKeys.add(lessonKey(occurrence));
            }
            List<ScheduleLessonEntity> weekLessons = lessonsByWeek.getOrDefault(monday, List.of());
            Set<String> actualKeys = weekLessons.stream().map(ScheduleCompletenessServiceImpl::lessonKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

            List<ScheduleCompletenessResponse.Gap> gaps = weekOccurrences.stream()
                .filter(occurrence -> !actualKeys.contains(lessonKey(occurrence)))
                .map(occurrence -> new ScheduleCompletenessResponse.Gap(occurrence.lessonDate(),
                    occurrence.weekday(), occurrence.timeSlotId(),
                    slotNames.getOrDefault(occurrence.timeSlotId(), "")))
                .toList();
            // 已生成/已完成/已停课一律按「格子」计数而不是按行数：结课或停课会让出格子
            // （见 ScheduleLessonServiceImpl 的 OCCUPYING_STATUSES 与 V72 的两枚占用防重键），
            // 同一格于是允许再排一节课进去。按行数计会让 generated 顶过 expected
            // （页面渲染成「4/3」），completedPercent 也会跟着虚高。
            Set<String> matchedKeys = actualKeys.stream()
                .filter(expectedKeys::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            // 按课时目标统计时分子是"这周实际排了几节"，不再只认计划格子：
            // 手工补到目标节奏上的课次同样抵缺口，否则设了目标的班永远排不满。
            // 此时 4/3 是真实超额，与上面那条"计划口径下不许出现 4/3"不冲突。
            Set<String> countedKeys = byTarget ? actualKeys : matchedKeys;
            int generated = countedKeys.size();
            int completed = matchedCellCount(weekLessons, countedKeys, LESSON_COMPLETED);
            int cancelled = matchedCellCount(weekLessons, countedKeys, LESSON_CANCELLED);
            // 计划外仍是行数：补课一节拆两节是真占了两个课时，不存在"同一格"去重的问题
            int extra = (int) weekLessons.stream()
                .filter(lesson -> !expectedKeys.contains(lessonKey(lesson)))
                .count();
            // 设了课时目标的班按目标数算应排：计划展开只是"这个格子排上了"，目标才是"这周该上几节"。
            // 门槛用"这一周计划有没有展开出格子"而不是 covered：单双周计划的空周、整周都被跳过的周，
            // 日期区间照样覆盖，用 covered 判会把目标摊进这些本来一节都不排的周里，报表天天报缺。
            int weekExpected = byTarget ? (weekOccurrences.isEmpty() ? 0 : target) : expectedKeys.size();
            int weekMissing = Math.max(0, weekExpected - generated);
            cells.add(new ScheduleCompletenessResponse.WeekCell(covered, weekExpected, generated, weekMissing,
                completed, cancelled, extra, gaps));
            totalExpected += weekExpected;
            totalGenerated += generated;
            totalCompleted += completed;
            totalMissing += weekMissing;
            totalExtra += extra;
        }
        LocalDate planEndDate = classPlans.stream().map(SchedulePlanEntity::getEndDate)
            .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        return new ScheduleCompletenessResponse.ClassRow(classId,
            clazz == null ? "班级#" + classId : clazz.getClassName(),
            clazz == null ? null : clazz.getCampusId(),
            clazz == null ? null : clazz.getTermId(),
            classPlans.size(),
            byTarget ? SOURCE_WEEKLY_TARGET : SOURCE_PLAN_EXPAND,
            byTarget ? target : null,
            progress == null ? null : progress.total(),
            progress == null ? null : progress.held(),
            progress == null ? null : progress.estimatedFinishDate(),
            planEndDate,
            cells,
            new ScheduleCompletenessResponse.RowSummary(totalExpected, totalGenerated, totalCompleted,
                totalMissing, totalExtra));
    }

    /**
     * 命中的计划格子里处于指定状态的格子数（按键去重）。
     * 同一格子有多行时（结课/停课让位后又排进一节）只算一个格子，保证 已完成 &le; 已生成 &le; 应排。
     */
    private static int matchedCellCount(List<ScheduleLessonEntity> weekLessons, Set<String> matchedKeys,
                                        String status) {
        return (int) weekLessons.stream()
            .filter(lesson -> status.equalsIgnoreCase(lesson.getLessonStatus()))
            .map(ScheduleCompletenessServiceImpl::lessonKey)
            .filter(matchedKeys::contains)
            .distinct()
            .count();
    }

    private static List<SchedulePlanEntity> plansFor(Long classId, List<SchedulePlanEntity> plans) {
        return plans.stream().filter(plan -> Objects.equals(plan.getClassId(), classId)).toList();
    }

    // ------------------------------------------------------------------ 课时进度

    /**
     * 一班的课时进度：总课时目标、已上节数、按近期节奏推算的预计结课日。
     * 只在班级设了「整班总课时」时才建，没设的行不给进度，免得页面上出现 0/0。
     */
    private record Progress(int total, int held, LocalDate estimatedFinishDate) {
    }

    /**
     * 批量取课时进度。
     *
     * <p>口径与格子里的「已完成」不同，这里是<b>全量</b>：日期已过、且没被停掉的课次一律算已上，
     * 不去比对计划格子。因为进度回答的是"这一班总共上掉几节了"，计划外加的课、
     * 改期补出来的课都真实消耗总课时；而格子里的「已完成」回答"这一格上完了没"。
     *
     * <p>不按统计窗口取数：窗口是操作员选的（常常只看本学期这几周），进度却是整班账，
     * 必须从开班看到现在。
     *
     * <p>预计结课日按<b>近四周</b>的节奏推算，只精确到"几周以后"。取近四周而不是开班至今，
     * 是为了让寒暑假、整段停课不稀释节奏——用全期均值会把结课日算得明显偏晚，而假期之后本来就要重排。
     * 近四周没上过课就算不出节奏，返回空让前端显示"暂无近期上课记录"，
     * 而不是给一个看起来很准、其实凭空推算的日期。
     */
    private Map<Long, Progress> progressFor(Long institutionId, List<Long> classIds,
                                            Map<Long, TeachingClassEntity> classById, LocalDate today) {
        List<Long> targets = classIds.stream()
            .map(classById::get)
            .filter(Objects::nonNull)
            .filter(clazz -> clazz.getTotalLessons() != null && clazz.getTotalLessons() > 0)
            .map(TeachingClassEntity::getId)
            .toList();
        if (targets.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ScheduleLessonEntity>> byClass = lessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLessonEntity>()
                    .eq(ScheduleLessonEntity::getInstitutionId, institutionId)
                    .in(ScheduleLessonEntity::getClassId, targets)
                    .lt(ScheduleLessonEntity::getLessonDate, today))
            .stream()
            .filter(lesson -> !LESSON_CANCELLED.equalsIgnoreCase(lesson.getLessonStatus()))
            .collect(Collectors.groupingBy(ScheduleLessonEntity::getClassId));
        LocalDate recentFrom = today.minusWeeks(RECENT_RATE_WEEKS);
        Map<Long, Progress> result = new LinkedHashMap<>();
        for (Long classId : targets) {
            List<ScheduleLessonEntity> held = byClass.getOrDefault(classId, List.of());
            int total = classById.get(classId).getTotalLessons();
            long recent = held.stream().filter(lesson -> !lesson.getLessonDate().isBefore(recentFrom)).count();
            result.put(classId, new Progress(total, held.size(),
                estimatedFinishDate(total, held.size(), recent, today)));
        }
        return result;
    }

    /** 剩余节数 ÷ 近期每周均节数 = 还要几周，向上取整；已上满或没有近期节奏可依时返回 null。 */
    private static LocalDate estimatedFinishDate(int total, int held, long recentHeld, LocalDate today) {
        int remaining = total - held;
        if (remaining <= 0) {
            return null;
        }
        double weeklyRate = (double) recentHeld / RECENT_RATE_WEEKS;
        if (weeklyRate <= 0) {
            return null;
        }
        return today.plusWeeks((long) Math.ceil(remaining / weeklyRate));
    }


    // ------------------------------------------------------------------ 取数与转换

    private Map<Long, String> slotNames(List<SchedulePlanEntity> plans) {
        Set<Long> slotIds = plans.stream()
            .map(plan -> readJson(plan.getSlotsJson(), new TypeReference<List<ScheduleSlotCell>>() {
            }))
            .flatMap(List::stream)
            .map(ScheduleSlotCell::timeSlotId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlotEntity>()
                .select(TimeSlotEntity::getId, TimeSlotEntity::getSlotName)
                .in(TimeSlotEntity::getId, slotIds))
            .forEach(slot -> names.put(slot.getId(), Objects.toString(slot.getSlotName(), "")));
        return names;
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

    private static String lessonKey(SchedulePlanExpander.Occurrence occurrence) {
        return occurrence.lessonDate() + "#" + occurrence.timeSlotId();
    }

    private static String lessonKey(ScheduleLessonEntity lesson) {
        return lesson.getLessonDate() + "#" + lesson.getTimeSlotId();
    }

    private static int percent(int part, int total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }
}
