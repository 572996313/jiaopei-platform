package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
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
    @Schema(description = "教师角色字典(teacher_role:lead=主讲,assistant=助教,substitute=代课)", allowableValues = {"lead", "assistant", "substitute"})
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
    @Schema(description = "状态(active=启用,inactive=停用;停用后不计入班级任课教师)",
            allowableValues = {"active", "inactive"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
