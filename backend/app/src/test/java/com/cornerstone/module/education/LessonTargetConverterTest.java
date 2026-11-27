package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.teachingclass.support.LessonTargetConverter;
import org.junit.jupiter.api.Test;

/**
 * 班级课时目标的录入口径换算：按月填的原值要能折算成报表用的每周节数，
 * 且折算不出 1 节时必须回落成"未设定"而不是 0。
 */
class LessonTargetConverterTest {

    @Test
    void weeklyModeKeepsTheInputAsIs() {
        assertEquals(3, LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_WEEKLY, 3));
        // 口径留空 = 按周填，与迁移列的 DEFAULT 'weekly' 同口径（老调用方只传 weeklyLessons 时行为不变）
        assertEquals(3, LessonTargetConverter.toWeekly(null, 3));
        assertEquals(3, LessonTargetConverter.toWeekly("  WEEKLY  ", 3));
    }

    @Test
    void monthlyModeRoundsToNearestWeek() {
        // 8 × 12 ÷ 52 = 1.85 → 2：一个月八次课，节奏上是每周一到两节，进位更贴近实际
        assertEquals(2, LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 8));
        assertEquals(2, LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 9));
        assertEquals(1, LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 4));
        assertEquals(1, LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 5));
        // 一年 52 周正好整除的档位
        assertEquals(3, LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 13));
    }

    @Test
    void monthlyTargetBelowOneLessonPerWeekBecomesUnset() {
        // 一个月一两次课折不出"每周 1 节"。存 0 会被读成"每周该上 0 节"，
        // 而读取侧判的是 != null && > 0，所以回落成 null 走计划展开，报表不会全绿却一节课没排
        assertNull(LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 1));
        assertNull(LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 2));
        assertNull(LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, 0));
        assertNull(LessonTargetConverter.toWeekly(LessonTargetConverter.MODE_MONTHLY, null));
    }

    @Test
    void unknownModeIsRejectedWithAllowedValues() {
        BusinessException error = assertThrows(BusinessException.class,
            () -> LessonTargetConverter.normalizeMode("term"));
        assertTrue(error.getMessage().contains("weekly"), error.getMessage());
        assertTrue(error.getMessage().contains("monthly"), error.getMessage());
    }
}
