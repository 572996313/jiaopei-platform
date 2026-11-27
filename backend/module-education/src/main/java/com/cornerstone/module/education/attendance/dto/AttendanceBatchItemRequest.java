package com.cornerstone.module.education.attendance.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "批量出勤单条记录")
public record AttendanceBatchItemRequest(
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学员ID不能为空")
    Long studentId,
    @Schema(description = "出勤状态(present=出勤,absent=缺勤,late=迟到,leave=请假)", allowableValues = {"present", "absent", "late", "leave"})
    @JsonAlias({"status", "attendance_status"})
    String attendanceStatus,
    @Schema(description = "数据来源(MANUAL/AUTO/IMPORT)")
    String source,
    @Schema(description = "备注")
    String remark
) {
}
