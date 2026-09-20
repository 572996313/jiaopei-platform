package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "排课请求")
public record ScheduleLessonRequest(
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
    @Schema(description = "时段ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "时段ID不能为空") Long timeSlotId,
    @Schema(description = "上课日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上课日期不能为空") LocalDate lessonDate,
    @Schema(description = "课次标题(≤128字符)")
    @Size(max = 128, message = "课次标题长度不能超过128个字符") String lessonTitle,
    @Schema(description = "课次状态(scheduled=已排课,rescheduled=已调课,cancelled=已停课,makeup=已补课,completed=已结课;不传默认已排课)", allowableValues = {"scheduled", "rescheduled", "cancelled", "makeup", "completed"})
    @Size(max = 32, message = "课次状态长度不能超过32个字符") String lessonStatus,
    @Schema(description = "变更类型(cancelled_makeup=停课补课;仅补课课次填写)", allowableValues = {"cancelled_makeup"})
    @Size(max = 32, message = "变更类型长度不能超过32个字符") String changeType,
    @Schema(description = "关联课次ID(变更/补课指向原课次)")
    Long relatedLessonId,
    @Schema(description = "授课教师ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "授课教师列表不能为空") List<Long> teacherIds,
    @Schema(description = "备注(≤500字符)")
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
