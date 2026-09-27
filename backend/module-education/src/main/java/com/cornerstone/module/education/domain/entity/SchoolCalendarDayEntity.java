package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_school_calendar_day")
@Schema(description = "校区日历停排日")
public class SchoolCalendarDayEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(0=机构级,全校区停排)")
    private Long campusId;

    @Schema(description = "停排日期")
    private LocalDate dayDate;

    @Schema(description = "名称(如 国庆节/校区检修,排课提示语里带出)")
    private String dayName;

    @Schema(description = "备注")
    private String remark;
}
