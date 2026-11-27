package com.cornerstone.module.education.schedule.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "校区日历停排日登记请求")
public record SchoolCalendarRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "校区ID(留空或 0 表示机构级,全校区停排)")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "停排日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("day_date")
    @NotNull(message = "停排日期不能为空")
    LocalDate dayDate,
    @Schema(description = "名称(如 国庆节/校区检修)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("day_name")
    @NotBlank(message = "名称不能为空")
    String dayName,
    @Schema(description = "备注")
    String remark
) {
}
