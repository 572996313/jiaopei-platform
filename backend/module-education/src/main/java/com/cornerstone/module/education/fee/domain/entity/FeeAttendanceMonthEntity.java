package com.cornerstone.module.education.fee.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 月度出勤天数：学员 × 账期的事实，按天收费项目共用一份。
 *
 * <p>不按项目拆，是因为"某学员 8 月来了 18 天"与收几个按天项目无关；
 * 拆成项目维度会出现伙食费 18 天、延时费 20 天两个互相矛盾的事实。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_fee_attendance_month")
@Schema(description = "月度出勤天数")
public class FeeAttendanceMonthEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(冗余)")
    private Long campusId;

    @Schema(description = "台账ID")
    private Long ledgerId;

    @Schema(description = "账期(当月1日)")
    private LocalDate billingMonth;

    @Schema(description = "生效出勤天数(可手工改)")
    private BigDecimal attendanceDays;

    @Schema(description = "考勤汇总自动值(保留对照)")
    private BigDecimal autoDays;

    @Schema(description = "是否手工覆盖(0=否,1=是)")
    private Boolean manualOverride;

    @Schema(description = "覆盖人ID")
    private Long overrideBy;

    @Schema(description = "覆盖时间")
    private LocalDateTime overrideAt;

    @Schema(description = "覆盖原因")
    private String overrideReason;

    @Schema(description = "来源(AUTO=考勤汇总,MANUAL=手工,IMPORT=导入)", allowableValues = {"AUTO", "MANUAL", "IMPORT"})
    private String source;

    @Schema(description = "备注")
    private String remark;
}
