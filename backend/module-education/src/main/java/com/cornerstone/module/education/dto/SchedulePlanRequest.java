package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "排课计划请求")
public record SchedulePlanRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "机构ID不能为空") Long institutionId,
    @Schema(description = "校区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "校区ID不能为空") Long campusId,
    @Schema(description = "学期ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "学期ID不能为空") Long termId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "班级ID不能为空") Long classId,
    @Schema(description = "教室ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "教室ID不能为空") Long classroomId,
    @Schema(description = "授课教师ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少需要一名授课教师") List<Long> teacherIds,
    @Schema(description = "每周节次模式(星期几+时段,允许一天多条)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "节次模式不能为空")
    @Valid List<ScheduleSlotCell> slots,
    @Schema(description = "排课频率(every_week=每周,odd_week=单周,even_week=双周;不传默认每周)",
            allowableValues = {"every_week", "odd_week", "even_week"})
    @Size(max = 16, message = "排课频率长度不能超过16个字符") String weekFrequency,
    @Schema(description = "生效起始日期(单双周锚点:所在自然周为第1周)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生效起始日期不能为空") LocalDate startDate,
    @Schema(description = "生效截止日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生效截止日期不能为空") LocalDate endDate,
    @Schema(description = "跳过日期列表(寒暑假/停课日,展开时整日略过)")
    List<LocalDate> skipDates,
    @Schema(description = "计划状态(active=启用,inactive=停用;不传默认启用;停用仅停止展开,不影响已生成课次)",
            allowableValues = {"active", "inactive"})
    @Size(max = 16, message = "计划状态长度不能超过16个字符") String status,
    @Schema(description = "备注(≤500字符)")
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
