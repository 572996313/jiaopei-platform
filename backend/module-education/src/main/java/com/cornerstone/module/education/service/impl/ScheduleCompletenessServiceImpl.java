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
 *   <li>应排只算计划展开出来的课次，跳过日期不计；没建计划的班级不出现在报表里。</li>
 *   <li>匹配按 {@code 日期#时段} 键而不是 planId：手工新增、补课、调课后的课次落在计划键之外，
 *       算「计划外」而不是补上缺口——缺口要回批量排课里补排才算补上。</li>
 *   <li>停课(cancelled)的课次仍算已生成（它确实占了那一格），单列显示。</li>
 * </ul>
 */
@Service
public class ScheduleCompletenessServiceImpl implements ScheduleCompletenessService {

    private static final String STATUS_ACTIVE = "active";
    private static final String LESSON_COMPLETED = "completed";
    private static final String LESSON_CANCELLED = "cancelled";
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

        List<SchedulePlanEntity> plans = planMapper.selectList(new LambdaQueryWrapper<SchedulePlanEntity>()
            .eq(SchedulePlanEntity::getInstitutionId, institutionId)
            .eq(SchedulePlanEntity::getStatus, STATUS_ACTIVE)
            .eq(query.getCampusId() != null, SchedulePlanEntity::getCampusId, query.getCampusId())
            .eq(query.getTermId() != null, SchedulePlanEntity::getTermId, query.getTermId())
            .eq(query.getClassId() != null, SchedulePlanEntity::getClassId, query.getClassId())
            .ge(SchedulePlanEntity::getEndDate, weekFrom)
            .le(SchedulePlanEntity::getStartDate, weekTo)
            .orderByAsc(SchedulePlanEntity::getStartDate));
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

        List<ScheduleCompletenessResponse.ClassRow> rows = new ArrayList<>();
        for (Long classId : classIds) {
            rows.add(buildRow(classId, plansFor(classId, plans), lessonsByClass.getOrDefault(classId, List.of()),
                classById.get(classId), slotNames, weekFrom, weekCount));
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
                                                           LocalDate weekFrom, int weekCount) {
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
            int extra = (int) weekLessons.stream()
                .filter(lesson -> !expectedKeys.contains(lessonKey(lesson)))
                .count();
            int generated = (int) weekLessons.stream().filter(lesson -> expectedKeys.contains(lessonKey(lesson))).count();
            int completed = (int) weekLessons.stream()
                .filter(lesson -> LESSON_COMPLETED.equalsIgnoreCase(lesson.getLessonStatus()))
                .count();
            int cancelled = (int) weekLessons.stream()
                .filter(lesson -> LESSON_CANCELLED.equalsIgnoreCase(lesson.getLessonStatus()))
                .count();
            cells.add(new ScheduleCompletenessResponse.WeekCell(covered, expectedKeys.size(), generated,
                completed, cancelled, extra, gaps));
            totalExpected += expectedKeys.size();
            totalGenerated += generated;
            totalCompleted += completed;
            totalMissing += gaps.size();
            totalExtra += extra;
        }
        return new ScheduleCompletenessResponse.ClassRow(classId,
            clazz == null ? "班级#" + classId : clazz.getClassName(),
            clazz == null ? null : clazz.getCampusId(),
            clazz == null ? null : clazz.getTermId(),
            classPlans.size(), cells,
            new ScheduleCompletenessResponse.RowSummary(totalExpected, totalGenerated, totalCompleted,
                totalMissing, totalExtra));
    }

    private static List<SchedulePlanEntity> plansFor(Long classId, List<SchedulePlanEntity> plans) {
        return plans.stream().filter(plan -> Objects.equals(plan.getClassId(), classId)).toList();
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
