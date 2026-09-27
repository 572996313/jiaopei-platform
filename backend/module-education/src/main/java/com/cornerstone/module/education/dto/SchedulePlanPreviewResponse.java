package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "排课计划展开预览响应(纯校验不落库,勾选后调提交接口生成课次)")
public record SchedulePlanPreviewResponse(
    @Schema(description = "课次草案列表(按计划展开顺序)")
    List<Item> items,
    @Schema(description = "草案总数")
    int totalCount,
    @Schema(description = "存在冲突的草案数")
    int conflictCount,
    @Schema(description = "被跳过日期命中的草案数")
    int skippedCount,
    @Schema(description = "提示列表(必排规则没覆盖到的格子、落在校区日历停排日的课次;仅提示不拦提交)")
    List<String> ruleNotices
) {
    @Schema(description = "课次草案条目")
    public record Item(
        @Schema(description = "周次(计划起始日所在自然周为第1周)")
        int weekNo,
        @Schema(description = "上课日期")
        LocalDate lessonDate,
        @Schema(description = "星期(1=周一,7=周日)")
        Integer weekday,
        @Schema(description = "时段ID")
        Long timeSlotId,
        @Schema(description = "时段名称(如 上1/下2)")
        String slotName,
        @Schema(description = "开始时间")
        LocalTime startTime,
        @Schema(description = "结束时间")
        LocalTime endTime,
        @Schema(description = "是否存在冲突(班级/教室/教师该日该节次已被占用、命中禁排规则,或该键已由此计划生成过课次)")
        boolean conflict,
        @Schema(description = "冲突说明列表")
        List<String> conflictMessages,
        @Schema(description = "是否命中跳过日期")
        boolean skipped,
        @Schema(description = "是否建议勾选(冲突或跳过为false)")
        boolean suggestedChecked
    ) {
    }
}
