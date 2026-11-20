package com.cornerstone.module.education.fee.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收费台账一维明细的导出行：每学员每收费项目一行。
 *
 * <p>列序与「收费出勤表」矩阵可以逐列对上，方便财务在 Excel 里自己做透视；
 * 金额列全部写计算后的数值（不写公式），打开即可筛选取和。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ColumnWidth(16)
@Schema(description = "收费台账明细导出行")
public class FeeLedgerDetailExportRow {

    @Schema(description = "校区")
    @ExcelProperty(value = "校区", index = 0)
    private String campusName;

    @Schema(description = "学期")
    @ExcelProperty(value = "学期", index = 1)
    private String termName;

    @Schema(description = "班级")
    @ExcelProperty(value = "班级", index = 2)
    private String className;

    @Schema(description = "学号")
    @ExcelProperty(value = "学号", index = 3)
    private String studentNo;

    @Schema(description = "学员姓名")
    @ExcelProperty(value = "学员姓名", index = 4)
    private String studentName;

    @Schema(description = "收费项目")
    @ExcelProperty(value = "收费项目", index = 5)
    private String itemName;

    @Schema(description = "计费方式(按月/按天)")
    @ExcelProperty(value = "计费方式", index = 6)
    private String billingModeLabel;

    @Schema(description = "收费月数")
    @ExcelProperty(value = "收费月数", index = 7)
    private BigDecimal billableMonths;

    @Schema(description = "月标准")
    @ExcelProperty(value = "月标准", index = 8)
    private BigDecimal monthlyStandard;

    @Schema(description = "日标准")
    @ExcelProperty(value = "日标准", index = 9)
    private BigDecimal dailyRate;

    @Schema(description = "上期结余")
    @ExcelProperty(value = "上期结余", index = 10)
    private BigDecimal openingBalance;

    @Schema(description = "其他抵扣")
    @ExcelProperty(value = "其他抵扣", index = 11)
    private BigDecimal otherDeduction;

    @Schema(description = "应收")
    @ExcelProperty(value = "应收金额", index = 12)
    private BigDecimal receivable;

    @Schema(description = "实收")
    @ExcelProperty(value = "实际收款", index = 13)
    private BigDecimal paid;

    @Schema(description = "未收费(负数=预收/多收)")
    @ExcelProperty(value = "未收费", index = 14)
    private BigDecimal unpaid;

    @Schema(description = "合计发生")
    @ExcelProperty(value = "合计发生", index = 15)
    private BigDecimal totalOccurrence;

    @Schema(description = "结余(即可退金额)")
    @ExcelProperty(value = "结余", index = 16)
    private BigDecimal balance;

    @Schema(description = "台账状态")
    @ExcelProperty(value = "台账状态", index = 17)
    private String statusLabel;

    @Schema(description = "备注")
    @ExcelProperty(value = "备注", index = 18)
    private String remark;
}
