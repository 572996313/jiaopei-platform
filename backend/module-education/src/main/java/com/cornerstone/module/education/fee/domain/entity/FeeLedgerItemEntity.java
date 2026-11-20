package com.cornerstone.module.education.fee.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应收台账明细：每学员每收费项目一行。
 *
 * <p>月标准与日标准是**建账当时的快照**，之后在价目表改价不会回写这里——
 * 已出的表不能因为改价而变数。要按新价重算，走台账的「重新取价」动作。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_fee_ledger_item")
@Schema(description = "应收台账明细")
public class FeeLedgerItemEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(冗余,供导出与数据范围收窄)")
    private Long campusId;

    @Schema(description = "台账ID")
    private Long ledgerId;

    @Schema(description = "收费项目ID")
    private Long feeItemId;

    @Schema(description = "计费方式快照(monthly=按月,daily=按天)", allowableValues = {"monthly", "daily"})
    private String billingMode;

    @Schema(description = "收费月数(可为小数,如 5.5)")
    private BigDecimal billableMonths;

    @Schema(description = "收费标准-月标准快照")
    private BigDecimal monthlyStandard;

    @Schema(description = "日标准快照(按天项目用)")
    private BigDecimal dailyRate;

    @Schema(description = "上期结余(正=预收,负=欠费)")
    private BigDecimal openingBalance;

    @Schema(description = "其他抵扣")
    private BigDecimal otherDeduction;

    @Schema(description = "取价来源(PRICE=价目表,STUDENT=学员覆盖,MANUAL=手工)",
            allowableValues = {"PRICE", "STUDENT", "MANUAL"})
    private String priceSource;

    @Schema(description = "取价来源的价目行ID")
    private Long priceId;

    @Schema(description = "上期结余来源明细ID(结转凭证,P2 启用)")
    private Long carryFromItemId;

    @Schema(description = "结转时间(P2 启用)")
    private LocalDateTime carryAt;

    @Schema(description = "备注")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
