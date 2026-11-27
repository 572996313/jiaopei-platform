package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "校区日历停排日")
public record SchoolCalendarView(
    @Schema(description = "主键ID") Long id,
    @Schema(description = "机构ID(多租户隔离)") Long institutionId,
    @Schema(description = "校区ID(0=机构级,全校区停排)") Long campusId,
    @Schema(description = "停排日期") LocalDate dayDate,
    @Schema(description = "名称(如 国庆节/校区检修)") String dayName,
    @Schema(description = "备注") String remark
) {
}
