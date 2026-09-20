package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "批量出勤单条记录")
public record AttendanceBatchItemRequest(
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学员ID不能为空")
    Long studentId,
    @Schema(description = "出勤状态(PRESENT/ABSENT/LATE/LEAVE/EARLY_LEAVE)", allowableValues = {"PRESENT", "ABSENT", "LATE", "LEAVE", "EARLY_LEAVE"})
    @JsonAlias({"status", "attendance_status"})
    String attendanceStatus,
    @Schema(description = "数据来源(MANUAL/AUTO/IMPORT)")
    String source,
    @Schema(description = "备注")
    String remark
) {
}
