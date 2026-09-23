package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
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
    @NotNull(message = "班级ID不能为空")
    Long classId,
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学员ID不能为空")
    Long studentId,
    @Schema(description = "出勤日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"date", "attendance_date"})
    @NotNull(message = "考勤日期不能为空")
    LocalDate attendanceDate,
    @Schema(description = "出勤状态(present=出勤,absent=缺勤,late=迟到,leave=请假)", allowableValues = {"present", "absent", "late", "leave"})
    @JsonAlias({"status", "attendance_status"})
    String attendanceStatus,
    @Schema(description = "数据来源(MANUAL=手动,AUTO=自动,IMPORT=导入)")
    String source,
    @Schema(description = "备注")
    String remark
) {
}
