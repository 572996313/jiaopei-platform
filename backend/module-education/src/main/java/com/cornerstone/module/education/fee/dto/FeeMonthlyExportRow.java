package com.cornerstone.module.education.fee.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 逐月发生额一维导出行：每学员每收费项目每个账期一行。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ColumnWidth(16)
@Schema(description = "逐月发生额导出行")
public class FeeMonthlyExportRow {

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

    @Schema(description = "账期(yyyy-MM)")
    @ExcelProperty(value = "账期", index = 6)
    private String billingMonth;

    @Schema(description = "月数系数")
    @ExcelProperty(value = "月数系数", index = 7)
    private BigDecimal monthFactor;

    @Schema(description = "出勤天数")
    @ExcelProperty(value = "出勤天数", index = 8)
    private BigDecimal attendanceDays;

    @Schema(description = "引擎自动金额")
    @ExcelProperty(value = "自动金额", index = 9)
    private BigDecimal autoAmount;

    @Schema(description = "最终发生额")
    @ExcelProperty(value = "最终发生额", index = 10)
    private BigDecimal occurrenceAmount;

    @Schema(description = "是否手工覆盖")
    @ExcelProperty(value = "手工覆盖", index = 11)
    private String manualOverrideLabel;

    @Schema(description = "备注")
    @ExcelProperty(value = "备注", index = 12)
    private String remark;
}
