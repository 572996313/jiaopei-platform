package com.cornerstone.module.education.teachingclass.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分班（班级学员关系）批量导入的 Excel 行模型。
 *
 * <p>按列下标读取，列顺序与 {@code XlsxImportTemplate.CLASS_STUDENT_HEADERS} 一字对应。
 * 校区编码选填，只在同一班级编码跨校区重码时用来消歧。</p>
 */
@Data
public class ClassStudentImportRow {

    @Schema(description = "班级编码,必填")
    @ExcelProperty(index = 0)
    private String classCode;

    @Schema(description = "校区编码,同一班级编码跨校区重码时必填")
    @ExcelProperty(index = 1)
    private String campusCode;

    @Schema(description = "学号,必填")
    @ExcelProperty(index = 2)
    private String studentNo;

    @Schema(description = "学员姓名,选填,填了会与学号对应档案核对")
    @ExcelProperty(index = 3)
    private String studentName;

    @Schema(description = "来源渠道,填字典(enrollment_channel)启用条目的中文名或item_value")
    @ExcelProperty(index = 4)
    private String channel;

    @Schema(description = "入班日期,文本 yyyy-MM-dd,留空按今天")
    @ExcelProperty(index = 5)
    private String joinedAt;

    @Schema(description = "备注")
    @ExcelProperty(index = 6)
    private String remark;
}
