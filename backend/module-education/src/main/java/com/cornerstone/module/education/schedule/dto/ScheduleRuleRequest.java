package com.cornerstone.module.education.schedule.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "排课规则请求")
public record ScheduleRuleRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "机构ID不能为空")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @Schema(description = "规则类型(forbidden=禁排,required=必排)", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"forbidden", "required"})
    @NotNull(message = "规则类型不能为空")
    @Size(max = 16, message = "规则类型长度不能超过16个字符") String ruleType,
    @Schema(description = "规则对象类型(class=班级,teacher=教师,classroom=教室)", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"class", "teacher", "classroom"})
    @NotNull(message = "规则对象类型不能为空")
    @Size(max = 16, message = "规则对象类型长度不能超过16个字符") String targetType,
    @Schema(description = "规则对象ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "规则对象ID不能为空")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long targetId,
    @Schema(description = "每周节次格子(星期几+时段)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少选择一个节次格子")
    @Valid List<ScheduleSlotCell> slots,
    @Schema(description = "生效起始日期(空=不限)")
    LocalDate startDate,
    @Schema(description = "生效截止日期(空=不限)")
    LocalDate endDate,
    @Schema(description = "规则状态(active=启用,inactive=停用;不传默认启用)", allowableValues = {"active", "inactive"})
    @Size(max = 16, message = "规则状态长度不能超过16个字符") String status,
    @Schema(description = "批注(≤500字符)")
    @Size(max = 500, message = "批注长度不能超过500个字符") String remark
) {
}
