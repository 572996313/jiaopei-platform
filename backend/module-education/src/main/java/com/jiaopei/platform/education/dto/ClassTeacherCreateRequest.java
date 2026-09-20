package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "班级教师创建请求")
public record ClassTeacherCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("class_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "班级ID不能为空")
    Long classId,
    @Schema(description = "教师ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("teacher_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "教师ID不能为空")
    Long teacherId,
    @Schema(description = "教师角色(HEAD=主讲,ASSIST=助教,COACH=辅导)", allowableValues = {"HEAD", "ASSIST", "COACH"})
    @JsonAlias("teacher_role")
    String teacherRole,
    @Schema(description = "是否主讲")
    @JsonAlias("is_primary")
    Boolean isPrimary,
    @Schema(description = "加入日期")
    @JsonAlias("joined_at")
    LocalDate joinedAt,
    @Schema(description = "离开日期")
    @JsonAlias("left_at")
    LocalDate leftAt,
    @Schema(description = "状态(ACTIVE=在岗,LEAVE=离岗)", allowableValues = {"ACTIVE", "LEAVE"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
