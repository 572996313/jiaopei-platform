package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cornerstone.module.education.schedule.dto.ScheduleSlotCell;
import com.cornerstone.module.education.schedule.support.SchedulePlanExpander;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulePlanExpanderTest {

    private static final ScheduleSlotCell MONDAY = new ScheduleSlotCell(1, 500L);

    @Test
    void expandEveryWeekProducesOneOccurrencePerWeek() {
        List<SchedulePlanExpander.Occurrence> occurrences = SchedulePlanExpander.expand(
            List.of(MONDAY), SchedulePlanExpander.EVERY_WEEK,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14));

        assertEquals(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8)),
            occurrences.stream().map(SchedulePlanExpander.Occurrence::lessonDate).toList());
        assertEquals(List.of(1, 2), occurrences.stream().map(SchedulePlanExpander.Occurrence::weekNo).toList());
    }

    @Test
    void expandOddAndEvenWeekFilterByWeekNo() {
        // 2026-06-01(周一)~2026-06-21(周日) 共 3 个自然周
        List<SchedulePlanExpander.Occurrence> odd = SchedulePlanExpander.expand(
            List.of(MONDAY), SchedulePlanExpander.ODD_WEEK,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 21));
        assertEquals(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)),
            odd.stream().map(SchedulePlanExpander.Occurrence::lessonDate).toList());

        List<SchedulePlanExpander.Occurrence> even = SchedulePlanExpander.expand(
            List.of(MONDAY), SchedulePlanExpander.EVEN_WEEK,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 21));
        assertEquals(List.of(LocalDate.of(2026, 6, 8)),
            even.stream().map(SchedulePlanExpander.Occurrence::lessonDate).toList());
    }

    @Test
    void expandTrimsPartialStartAndEndWeeks() {
        // 起始日是周三,第1周的周一(6/1)早于起始日应被裁掉,首条落在第2周
        List<SchedulePlanExpander.Occurrence> occurrences = SchedulePlanExpander.expand(
            List.of(MONDAY), SchedulePlanExpander.EVERY_WEEK,
            LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 14));

        assertEquals(1, occurrences.size());
        assertEquals(2, occurrences.getFirst().weekNo());
        assertEquals(LocalDate.of(2026, 6, 8), occurrences.getFirst().lessonDate());
    }

    @Test
    void expandDeduplicatesSlots() {
        List<SchedulePlanExpander.Occurrence> occurrences = SchedulePlanExpander.expand(
            List.of(MONDAY, MONDAY), SchedulePlanExpander.EVERY_WEEK,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));

        assertEquals(1, occurrences.size());
    }

    @Test
    void expandRejectsRangeOverMaxWeeksAndInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> SchedulePlanExpander.expand(
            List.of(MONDAY), SchedulePlanExpander.EVERY_WEEK,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1).plusWeeks(SchedulePlanExpander.MAX_WEEKS)));
        assertThrows(IllegalArgumentException.class, () -> SchedulePlanExpander.expand(
            List.of(MONDAY), "monthly", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)));
        assertThrows(IllegalArgumentException.class, () -> SchedulePlanExpander.expand(
            List.of(MONDAY), SchedulePlanExpander.EVERY_WEEK,
            LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 1)));
    }
}
