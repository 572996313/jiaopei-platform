package com.cornerstone.module.education.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 教师档案批量导入的 Excel 行模型。
 *
 * <p>按列下标而不是列名取值：客户会在模板里改表头文字，按下标读才不会整批解析成空值。
 * 列顺序与 {@code TeacherImportTemplate.HEADERS} 一字对应，改一处必须改另一处。</p>
 */
@Data
public class TeacherImportRow {

    @Schema(description = "教师编码,本机构内唯一,已存在则跳过新建")
    @ExcelProperty(index = 0)
    private String teacherCode;

    @Schema(description = "教师姓名")
    @ExcelProperty(index = 1)
    private String teacherName;

    @Schema(description = "手机号,本机构内唯一,后续按它匹配登录账号")
    @ExcelProperty(index = 2)
    private String phone;

    @Schema(description = "校区编码,取值见校区管理页;留空表示暂不归属校区")
    @ExcelProperty(index = 3)
    private String campusCode;

    @Schema(description = "性别,填字典(gender)启用条目的中文名或item_value,留空为未填写")
    @ExcelProperty(index = 4)
    private String gender;

    @Schema(description = "职称")
    @ExcelProperty(index = 5)
    private String title;

    @Schema(description = "用工类型,填字典(employment_type)启用条目的中文名或item_value,留空按 full_time")
    @ExcelProperty(index = 6)
    private String employmentType;

    @Schema(description = "入职日期,文本 yyyy-MM-dd")
    @ExcelProperty(index = 7)
    private String hiredAt;

    @Schema(description = "教师状态,填字典(teacher_status)启用条目的中文名或item_value,留空按 active")
    @ExcelProperty(index = 8)
    private String status;

    @Schema(description = "备注")
    @ExcelProperty(index = 9)
    private String remark;
}
