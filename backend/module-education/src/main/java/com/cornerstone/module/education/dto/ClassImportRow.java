package com.cornerstone.module.education.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 班级档案批量导入的 Excel 行模型。
 *
 * <p>按列下标读取，列顺序与 {@code XlsxImportTemplate.CLASS_HEADERS} 一字对应。
 * 校区编码必填：班级编码/名称的唯一键是「校区ID + 业务键」，不是机构级。</p>
 */
@Data
public class ClassImportRow {

    @Schema(description = "班级编码,留空按编号规则自动生成,填了必须同一校区内唯一")
    @ExcelProperty(index = 0)
    private String classCode;

    @Schema(description = "班级名称,同一校区内唯一")
    @ExcelProperty(index = 1)
    private String className;

    @Schema(description = "校区编码,必填,取值见校区管理页")
    @ExcelProperty(index = 2)
    private String campusCode;

    @Schema(description = "学期编码")
    @ExcelProperty(index = 3)
    private String termCode;

    @Schema(description = "课程编码")
    @ExcelProperty(index = 4)
    private String courseCode;

    @Schema(description = "班型编码")
    @ExcelProperty(index = 5)
    private String classTypeCode;

    @Schema(description = "主讲教师编码")
    @ExcelProperty(index = 6)
    private String teacherCode;

    @Schema(description = "容量上限,0或留空表示不限")
    @ExcelProperty(index = 7)
    private String capacityLimit;

    @Schema(description = "每周应上节数,留空表示未设定")
    @ExcelProperty(index = 8)
    private String weeklyLessons;

    @Schema(description = "开班日期,文本 yyyy-MM-dd,允许未来日期")
    @ExcelProperty(index = 9)
    private String openedAt;

    @Schema(description = "状态,代码判定值:planning/active/inactive/closed,留空按 planning")
    @ExcelProperty(index = 10)
    private String status;

    @Schema(description = "备注")
    @ExcelProperty(index = 11)
    private String remark;
}
