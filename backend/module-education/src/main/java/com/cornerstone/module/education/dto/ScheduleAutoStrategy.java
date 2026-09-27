package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 自动排课的择优口径。只有三个开关，因为引擎能真正影响的只有"落在哪个空位"：
 * 教师与教室来自班级既有计划，引擎不换人也不换房。
 *
 * <p>教师周/日上限不在这里——那是档案上的硬事实，引擎一律遵守（手工排课只提示，因为有人在判断）。
 */
@Schema(description = "自动排课择优策略")
public record ScheduleAutoStrategy(
    @Schema(description = "优先落在该班这一周已经排过课的日子(少来一趟),空=是")
    Boolean concentrateClassDays,
    @Schema(description = "同一天内优先贴着该班已有课次的相邻节次(避免上下午空档),空=是")
    Boolean preferAdjacentSlot,
    @Schema(description = "该班单日最多排几节(自动补排部分;NULL=不限,不含已有课次)")
    Integer maxDailyLessonsPerClass
) {
    public boolean concentrateClassDaysOrDefault() {
        return concentrateClassDays == null || concentrateClassDays;
    }

    public boolean preferAdjacentSlotOrDefault() {
        return preferAdjacentSlot == null || preferAdjacentSlot;
    }

    /** 每日上限只有正数才有意义：0 与负数都按"不限"处理，与 weekly_lessons 的 NULL=未设定同口径。 */
    public Integer maxDailyPerClassOrNull() {
        return maxDailyLessonsPerClass != null && maxDailyLessonsPerClass > 0 ? maxDailyLessonsPerClass : null;
    }
}
