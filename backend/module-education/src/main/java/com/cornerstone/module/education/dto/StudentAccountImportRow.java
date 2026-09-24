package com.cornerstone.module.education.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 账号批量导入的 Excel 行模型。
 *
 * <p>按列下标而不是列名取值：客户会在模板里改表头文字，按下标读才不会整批解析成空值。</p>
 */
@Data
public class StudentAccountImportRow {

    @Schema(description = "学员编号")
    @ExcelProperty(index = 0)
    private String studentNo;

    @Schema(description = "学员姓名,选填,填了就必须与档案一致")
    @ExcelProperty(index = 1)
    private String studentName;

    @Schema(description = "账号类型(家长/学生本人)")
    @ExcelProperty(index = 2)
    private String accountKind;

    @Schema(description = "账号姓名,作为登录账号的显示名称")
    @ExcelProperty(index = 3)
    private String displayName;

    @Schema(description = "手机号,选填,学生没有手机可留空")
    @ExcelProperty(index = 4)
    private String phone;

    @Schema(description = "登录账号,留空则依次取手机号、学员编号")
    @ExcelProperty(index = 5)
    private String username;

    @Schema(description = "备注")
    @ExcelProperty(index = 6)
    private String remark;
}
