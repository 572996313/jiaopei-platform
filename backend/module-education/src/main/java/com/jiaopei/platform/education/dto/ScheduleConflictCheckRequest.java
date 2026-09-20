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
    @NotNull(message = "机构ID不能为空")Long institutionId,
    @Schema(description = "校区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "校区ID不能为空")Long campusId,
    @Schema(description = "学期ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "学期ID不能为空")Long termId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "班级ID不能为空")Long classId,
    @Schema(description = "教室ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "教室ID不能为空")Long classroomId,
    @Schema(description = "时段ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "时段ID不能为空")Long timeSlotId,
    @Schema(description = "上课日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上课日期不能为空")LocalDate lessonDate,
    @Schema(description = "授课教师ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "授课教师列表不能为空")List<Long> teacherIds
) {
}
