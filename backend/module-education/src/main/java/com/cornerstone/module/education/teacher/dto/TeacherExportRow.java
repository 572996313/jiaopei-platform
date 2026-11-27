package com.cornerstone.module.education.teacher.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教师档案导出的 Excel 行模型。
 *
 * <p>列顺序与导入模板一致（多出的「关联账号」列放在末尾），配合填写说明页就是
 * 一份能改完再导回去的回执。取值一律用规范值：字典 item_value、校区编码，不写中文标签。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ColumnWidth(18)
@Schema(description = "教师档案导出行")
public class TeacherExportRow {

    @Schema(description = "教师编码")
    @ExcelProperty(value = "教师编码", index = 0)
    private String teacherCode;

    @Schema(description = "教师姓名")
    @ExcelProperty(value = "教师姓名", index = 1)
    private String teacherName;

    @Schema(description = "手机号")
    @ExcelProperty(value = "手机号", index = 2)
    private String phone;

    @Schema(description = "校区编码")
    @ExcelProperty(value = "校区编码", index = 3)
    private String campusCode;

    @Schema(description = "性别中文名(字典gender的item_name；字典里没有该值时回显原始item_value)")
    @ExcelProperty(value = "性别", index = 4)
    private String gender;

    @Schema(description = "职称")
    @ExcelProperty(value = "职称", index = 5)
    private String title;

    @Schema(description = "用工类型中文名(字典employment_type的item_name；字典里没有该值时回显原始item_value)")
    @ExcelProperty(value = "用工类型", index = 6)
    private String employmentType;

    @Schema(description = "入职日期(yyyy-MM-dd)")
    @ExcelProperty(value = "入职日期", index = 7)
    private String hiredAt;

    @Schema(description = "教师状态中文名(字典teacher_status的item_name；字典里没有该值时回显原始item_value)")
    @ExcelProperty(value = "状态", index = 8)
    private String status;

    @Schema(description = "备注")
    @ExcelProperty(value = "备注", index = 9)
    private String remark;

    @Schema(description = "关联登录账号,空=尚未绑定账号")
    @ExcelProperty(value = "关联账号", index = 10)
    private String username;
}
