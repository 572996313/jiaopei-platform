package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "账号批量导入的单行结果(校验回显与错误明细)")
public record StudentAccountImportRowView(
    @Schema(description = "Excel 行号,从 2 开始(第 1 行是表头)") Integer rowNum,

    @Schema(description = "学员编号") String studentNo,

    @Schema(description = "学员档案中的姓名,未找到档案时为空") String studentName,

    @Schema(description = "账号类型(家长=为监护人建账号,学生本人=为学员自己建账号)",
            allowableValues = {"家长", "学生本人"}) String accountKind,

    @Schema(description = "账号显示名称") String displayName,

    @Schema(description = "登录账号,未填时回落为手机号") String username,

    @Schema(description = "手机号") String phone,

    @Schema(description = "账号用户ID(校验阶段命中已有账号时回显,导入阶段为新建或复用的账号ID),供批量发码勾选") Long userId,

    @Schema(description = "匹配到的学员ID,未匹配为空") Long studentId,

    @Schema(description = "是否通过校验") Boolean valid,

    @Schema(description = "不通过原因,多个原因用分号连接") String error,

    @Schema(description = "提示(不阻断导入),如账号已存在仅补绑定") String notice
) {
}
