package com.cornerstone.module.education.schedule.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "排课计划提交请求(保存计划并按勾选项生成课次)")
public record SchedulePlanCommitRequest(
    @Schema(description = "计划ID(为空则新建计划;不为空则按请求内容更新该计划后增量补排)")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long planId,
    @Schema(description = "计划定义", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "计划定义不能为空")
    @Valid SchedulePlanRequest plan,
    @Schema(description = "勾选生成的课次键列表(必须包含于按计划展开的草案)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少勾选一个课次") List<SchedulePlanOccurrence> lessons
) {
}
