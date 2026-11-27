package com.cornerstone.module.education.student.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 学员档案批量导入的 Excel 行模型。
 *
 * <p>按列下标而不是列名取值：客户会在模板里改表头文字，按下标读才不会整批解析成空值。
 * 列顺序与 {@code XlsxImportTemplate.STUDENT_HEADERS} 一字对应，改一处必须改另一处。</p>
 */
@Data
public class StudentImportRow {

    @Schema(description = "学号,留空按编号规则自动生成,填了必须本机构内唯一")
    @ExcelProperty(index = 0)
    private String studentNo;

    @Schema(description = "学员姓名")
    @ExcelProperty(index = 1)
    private String studentName;

    @Schema(description = "性别,填字典(gender)启用条目的中文名或item_value")
    @ExcelProperty(index = 2)
    private String gender;

    @Schema(description = "出生日期,文本 yyyy-MM-dd")
    @ExcelProperty(index = 3)
    private String birthday;

    @Schema(description = "监护人姓名")
    @ExcelProperty(index = 4)
    private String guardianName;

    @Schema(description = "监护人联系电话")
    @ExcelProperty(index = 5)
    private String guardianPhone;

    @Schema(description = "学员状态,填字典(student_status)启用条目的中文名或item_value,留空按 studying")
    @ExcelProperty(index = 6)
    private String status;

    @Schema(description = "入学日期,文本 yyyy-MM-dd")
    @ExcelProperty(index = 7)
    private String enrolledAt;

    @Schema(description = "备注")
    @ExcelProperty(index = 8)
    private String remark;
}
