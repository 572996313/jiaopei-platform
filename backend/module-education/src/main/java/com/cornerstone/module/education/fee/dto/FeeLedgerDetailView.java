package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "收费台账详情(头 + 明细 + 逐月 + 出勤 + 收款)")
public record FeeLedgerDetailView(
    @Schema(description = "台账ID") Long id,
    @Schema(description = "机构ID") Long institutionId,
    @Schema(description = "校区ID") Long campusId,
    @Schema(description = "校区名称") String campusName,
    @Schema(description = "班级ID") Long classId,
    @Schema(description = "班级名称") String className,
    @Schema(description = "学期ID") Long termId,
    @Schema(description = "学期名称") String termName,
    @Schema(description = "学期起始日") java.time.LocalDate termStartDate,
    @Schema(description = "学期结束日") java.time.LocalDate termEndDate,
    @Schema(description = "学员ID") Long studentId,
    @Schema(description = "学号") String studentNo,
    @Schema(description = "学员姓名") String studentName,
    @Schema(description = "状态") String status,
    @Schema(description = "备注") String remark,
    @Schema(description = "台账明细") List<FeeLedgerItemView> items,
    @Schema(description = "逐月发生额") List<FeeMonthlyView> monthly,
    @Schema(description = "月度出勤") List<FeeAttendanceView> attendance,
    @Schema(description = "收款流水") List<FeePaymentView> payments,
    @Schema(description = "应收合计") BigDecimal receivable,
    @Schema(description = "实收合计") BigDecimal paid,
    @Schema(description = "未收费合计") BigDecimal unpaid,
    @Schema(description = "合计发生") BigDecimal totalOccurrence,
    @Schema(description = "结余(即可退金额)") BigDecimal balance
) {
}
