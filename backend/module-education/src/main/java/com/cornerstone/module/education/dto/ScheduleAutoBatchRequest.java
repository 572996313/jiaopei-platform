package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "自动排课(按缺口补排)请求")
public record ScheduleAutoBatchRequest(
    @Schema(description = "机构ID(多租户隔离;服务端与登录机构比对)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "机构ID不能为空") Long institutionId,
    @Schema(description = "校区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "校区ID不能为空") Long campusId,
    @Schema(description = "学期ID(本批生成的课次统一挂该学期)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "学期ID不能为空") Long termId,
    @Schema(description = "补排窗口起始日期(不早于今天)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "起始日期不能为空") LocalDate startDate,
    @Schema(description = "补排窗口截止日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "截止日期不能为空") LocalDate endDate,
    @Schema(description = "班级ID列表,空=该校区全部有启用中计划的班级") List<Long> classIds,
    @Schema(description = "择优策略,空=全部默认") ScheduleAutoStrategy strategy,
    @Schema(description = "备注") String remark
) {
}
