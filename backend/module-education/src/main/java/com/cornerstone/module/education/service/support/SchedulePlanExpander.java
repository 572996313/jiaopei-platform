package com.cornerstone.module.education.service.support;

import com.cornerstone.module.education.dto.ScheduleSlotCell;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 排课计划展开器:把"每周节次模式 + 频率 + 起止日期"展开为逐日课次草案。
 * 纯函数,不做冲突检测;单双周锚点为起始日期所在自然周(第1周)。
 */
public final class SchedulePlanExpander {

    public static final String EVERY_WEEK = "every_week";
    public static final String ODD_WEEK = "odd_week";
    public static final String EVEN_WEEK = "even_week";

    /** 起止日期允许的最大跨度(周),防御误填十年日期导致展开爆炸 */
    public static final int MAX_WEEKS = 120;

    private SchedulePlanExpander() {
    }

    public record Occurrence(int weekNo, LocalDate lessonDate, Integer weekday, Long timeSlotId) {
    }

    public static List<Occurrence> expand(List<ScheduleSlotCell> slots, String weekFrequency,
                                          LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("计划起止日期无效");
        }
        Set<ScheduleSlotCell> distinctSlots = new LinkedHashSet<>(slots);
        LocalDate anchorMonday = startDate.with(DayOfWeek.MONDAY);
        long weekCount = ChronoUnit.WEEKS.between(anchorMonday, endDate.with(DayOfWeek.MONDAY)) + 1;
        if (weekCount > MAX_WEEKS) {
            throw new IllegalArgumentException("计划日期范围不能超过" + MAX_WEEKS + "周");
        }
        List<Occurrence> occurrences = new ArrayList<>();
        for (int weekNo = 1; weekNo <= weekCount; weekNo++) {
            if (!matchesFrequency(weekFrequency, weekNo)) {
                continue;
            }
            LocalDate monday = anchorMonday.plusWeeks(weekNo - 1L);
            for (ScheduleSlotCell slot : distinctSlots) {
                LocalDate date = monday.plusDays(slot.weekday() - 1L);
                if (date.isBefore(startDate) || date.isAfter(endDate)) {
                    continue;
                }
                occurrences.add(new Occurrence(weekNo, date, slot.weekday(), slot.timeSlotId()));
            }
        }
        return occurrences;
    }

    private static boolean matchesFrequency(String weekFrequency, int weekNo) {
        if (weekFrequency == null || EVERY_WEEK.equals(weekFrequency)) {
            return true;
        }
        if (ODD_WEEK.equals(weekFrequency)) {
            return weekNo % 2 == 1;
        }
        if (EVEN_WEEK.equals(weekFrequency)) {
            return weekNo % 2 == 0;
        }
        throw new IllegalArgumentException("排课频率无效：" + weekFrequency);
    }
}
