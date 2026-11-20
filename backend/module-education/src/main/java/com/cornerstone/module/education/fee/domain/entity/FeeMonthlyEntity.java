package com.cornerstone.module.education.fee.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_fee_monthly")
@Schema(description = "逐月发生额")
public class FeeMonthlyEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(冗余)")
    private Long campusId;

    @Schema(description = "台账ID")
    private Long ledgerId;

    @Schema(description = "台账明细ID")
    private Long ledgerItemId;

    @Schema(description = "账期(当月1日,如 2026-08-01)")
    private LocalDate billingMonth;

    @Schema(description = "月数系数(1.00=整月,0.50=半月;按天项目不使用)")
    private BigDecimal monthFactor;

    @Schema(description = "引擎自动金额(保留对账与回显)")
    private BigDecimal autoAmount;

    @Schema(description = "最终发生额(覆盖后)")
    private BigDecimal occurrenceAmount;

    @Schema(description = "是否手工覆盖金额(0=否,1=是)")
    private Boolean manualOverride;

    @Schema(description = "覆盖人ID")
    private Long overrideBy;

    @Schema(description = "覆盖时间")
    private LocalDateTime overrideAt;

    @Schema(description = "覆盖原因")
    private String overrideReason;

    @Schema(description = "备注")
    private String remark;
}
