package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "教学班级创建请求")
public record TeachingClassCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @Schema(description = "校区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "campusId is required")
    Long campusId,
    @Schema(description = "学期ID")
    @JsonAlias("term_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long termId,
    @Schema(description = "课程ID")
    @JsonAlias("course_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long courseId,
    @Schema(description = "班级类型ID")
    @JsonAlias("class_type_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long classTypeId,
    @Schema(description = "班级编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "class_code"})
    @NotBlank(message = "classCode is required")
    String classCode,
    @Schema(description = "班级名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "class_name"})
    @NotBlank(message = "className is required")
    String className,
    @Schema(description = "主教师ID")
    @JsonAlias({"primary_teacher_id", "teacher_id"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long teacherId,
    @Schema(description = "容量上限")
    @JsonAlias({"capacity", "capacity_limit"})
    @Min(value = 0, message = "capacityLimit must be greater than or equal to 0")
    Integer capacityLimit,
    @Schema(description = "当前人数")
    @JsonAlias("current_students")
    @Min(value = 0, message = "currentStudents must be greater than or equal to 0")
    Integer currentStudents,
    @Schema(description = "状态(PLANNED=筹备中,OPEN=开班中,CLOSED=已结课,CANCELLED=已取消)", allowableValues = {"PLANNED", "OPEN", "CLOSED", "CANCELLED"})
    String status,
    @Schema(description = "开班日期")
    @JsonAlias("opened_at")
    LocalDate openedAt,
    @Schema(description = "备注")
    String remark
) {
}
