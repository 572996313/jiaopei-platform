package com.jiaopei.platform.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "课次信息视图")
public record AttendanceLessonInfoView(
    @Schema(description = "课次ID")
    Long lessonId,
    @Schema(description = "机构ID")
    Long institutionId,
    @Schema(description = "校区ID")
    Long campusId,
    @Schema(description = "班级ID")
    Long classId,
    @Schema(description = "班级名称")
    String className,
    @Schema(description = "上课日期")
    LocalDate lessonDate,
    @Schema(description = "开始时间")
    LocalTime startTime,
    @Schema(description = "结束时间")
    LocalTime endTime,
    @Schema(description = "课次标题")
    String lessonTitle,
    @Schema(description = "课次状态(SCHEDULED/TAKEN/CANCELLED/MAKEUP)", allowableValues = {"SCHEDULED", "TAKEN", "CANCELLED", "MAKEUP"})
    String lessonStatus,
    @Schema(description = "备注")
    String remark
) {
}
