package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "收费台账列表行(派生值由服务端现算)")
public record FeeLedgerRowView(
    @Schema(description = "台账ID") Long id,
    @Schema(description = "机构ID") Long institutionId,
    @Schema(description = "校区ID") Long campusId,
    @Schema(description = "校区名称") String campusName,
    @Schema(description = "班级ID") Long classId,
    @Schema(description = "班级名称") String className,
    @Schema(description = "学期ID") Long termId,
    @Schema(description = "学期名称") String termName,
    @Schema(description = "学员ID") Long studentId,
    @Schema(description = "学号") String studentNo,
    @Schema(description = "学员姓名") String studentName,
    @Schema(description = "状态(active=在收,settled=已结清)") String status,
    @Schema(description = "收费项目行数") int itemCount,
    @Schema(description = "应收") BigDecimal receivable,
    @Schema(description = "实收") BigDecimal paid,
    @Schema(description = "未收费(负数=预收/多收)") BigDecimal unpaid,
    @Schema(description = "合计发生") BigDecimal totalOccurrence,
    @Schema(description = "结余(即可退金额)") BigDecimal balance,
    @Schema(description = "备注") String remark
) {
}
