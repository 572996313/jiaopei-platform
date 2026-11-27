package com.cornerstone.module.education.teachingclass.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    @NotNull(message = "班级ID不能为空")
    Long classId,
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学员ID不能为空")
    Long studentId,
    @Schema(description = "状态(active=在读,quit=退班,graduated=结业,reserved=预占)", allowableValues = {"active", "quit", "graduated", "reserved"})
    String status,
    @Schema(description = "报名渠道字典(enrollment_channel:front_desk=前台报名,consultant=顾问录入,online=线上线索,referral=转介绍)", allowableValues = {"front_desk", "consultant", "online", "referral"})
    String channel,
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
