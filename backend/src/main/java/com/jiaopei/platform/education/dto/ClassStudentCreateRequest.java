package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "班级学员创建请求")
public record ClassStudentCreateRequest(
    @Schema(description = "机构ID(多租户隔离)")
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"class_id", "teachingClassId"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "classId 不能为空")
    Long classId,
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "studentId 不能为空")
    Long studentId,
    @Schema(description = "状态(ACTIVE=在读,QUIT=退班,GRADUATED=结业,RESERVED=预占)", allowableValues = {"ACTIVE", "QUIT", "GRADUATED", "RESERVED"})
    String status,
    @Schema(description = "加入日期")
    @JsonAlias("joined_at")
    LocalDate joinedAt,
    @Schema(description = "离开日期")
    @JsonAlias("left_at")
    LocalDate leftAt,
    @Schema(description = "备注")
    String remark
) {
}
