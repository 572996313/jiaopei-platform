package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 月度出勤汇总的一行（Mapper 直接映射，不走接口序列化）。 */
@Data
@Schema(description = "月度出勤汇总行")
public class FeeAttendanceDayRow {

    @Schema(description = "学员ID")
    private Long studentId;

    @Schema(description = "出勤天数")
    private Long dayCount;
}
