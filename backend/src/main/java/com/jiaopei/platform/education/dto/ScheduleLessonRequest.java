package com.jiaopei.platform.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "排课请求")
public record ScheduleLessonRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Long institutionId,
    @Schema(description = "校区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Long campusId,
    @Schema(description = "学期ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Long termId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Long classId,
    @Schema(description = "教室ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Long classroomId,
    @Schema(description = "时段ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Long timeSlotId,
    @Schema(description = "上课日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull LocalDate lessonDate,
    @Schema(description = "课次标题(≤128字符)")
    @Size(max = 128) String lessonTitle,
    @Schema(description = "课次状态(SCHEDULED/TAKEN/CANCELLED/MAKEUP)", allowableValues = {"SCHEDULED", "TAKEN", "CANCELLED", "MAKEUP"})
    @Size(max = 32) String lessonStatus,
    @Schema(description = "变更类型(STOP/MAKEUP/ADJUST/RESCHEDULE)", allowableValues = {"STOP", "MAKEUP", "ADJUST", "RESCHEDULE"})
    @Size(max = 32) String changeType,
    @Schema(description = "关联课次ID(变更/补课指向原课次)")
    Long relatedLessonId,
    @Schema(description = "授课教师ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull List<Long> teacherIds,
    @Schema(description = "备注(≤500字符)")
    @Size(max = 500) String remark
) {
}
