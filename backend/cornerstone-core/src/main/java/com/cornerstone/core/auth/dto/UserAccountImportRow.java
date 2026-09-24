package com.cornerstone.core.auth.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户账号批量导入的 Excel 行模型。
 *
 * <p>按列下标而不是列名取值：客户会在模板里改表头文字，按下标读才不会整批解析成空值。</p>
 */
@Data
public class UserAccountImportRow {

    @Schema(description = "登录账号,全系统唯一,作为登录凭证")
    @ExcelProperty(index = 0)
    private String username;

    @Schema(description = "显示名称,登录后展示的名字")
    @ExcelProperty(index = 1)
    private String displayName;

    @Schema(description = "初始口令,必填;留空会被系统兜成一个公共默认口令,所以整列不许空")
    @ExcelProperty(index = 2)
    private String initialPassword;

    @Schema(description = "手机号,选填,只用于展示与检索,登录不认它")
    @ExcelProperty(index = 3)
    private String phone;

    @Schema(description = "角色编码,多个用逗号分隔,取值见 sys_role.role_code")
    @ExcelProperty(index = 4)
    private String roleCodes;

    @Schema(description = "状态,选填,留空为启用(active)")
    @ExcelProperty(index = 5)
    private String status;

    @Schema(description = "备注")
    @ExcelProperty(index = 6)
    private String remark;
}
