package com.jiaopei.platform.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "排课冲突检测请求")
public record ScheduleConflictCheckRequest(
    @Schema(description = "排除的课次ID(编辑现有课次时传入)")
    Long excludeLessonId,
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
    @Schema(description = "授课教师ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull List<Long> teacherIds
) {
}
