package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "家长端收费台账摘要(仅展示费用口径,不含凭证号等内部字段)")
public record MiniappFeeLedgerView(
    @Schema(description = "台账ID")
    Long id,

    @Schema(description = "班级名称")
    String className,

    @Schema(description = "学期名称")
    String termName,

    @Schema(description = "状态(active=在收,settled=已结清)")
    String status,

    @Schema(description = "应收")
    BigDecimal receivable,

    @Schema(description = "实收")
    BigDecimal paid,

    @Schema(description = "未收(负数=预收/多收)")
    BigDecimal unpaid,

    @Schema(description = "结余(正数=可退金额)")
    BigDecimal balance,

    @Schema(description = "收费项目明细")
    List<MiniappFeeItemView> items
) {
}
