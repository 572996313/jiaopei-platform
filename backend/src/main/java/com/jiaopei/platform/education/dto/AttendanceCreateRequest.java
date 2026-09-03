package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "出勤创建请求")
public record AttendanceCreateRequest(
    @Schema(description = "机构ID(多租户隔离)")
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @Schema(description = "课次ID")
    @JsonAlias("lesson_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long lessonId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"class_id", "teachingClassId"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "classId is required")
    Long classId,
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "studentId is required")
    Long studentId,
    @Schema(description = "出勤日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"date", "attendance_date"})
    @NotNull(message = "attendanceDate is required")
    LocalDate attendanceDate,
    @Schema(description = "出勤状态(PRESENT/ABSENT/LATE/LEAVE/EARLY_LEAVE)", allowableValues = {"PRESENT", "ABSENT", "LATE", "LEAVE", "EARLY_LEAVE"})
    @JsonAlias({"status", "attendance_status"})
    String attendanceStatus,
    @Schema(description = "数据来源(MANUAL=手动,AUTO=自动,IMPORT=导入)")
    String source,
    @Schema(description = "备注")
    String remark
) {
}
