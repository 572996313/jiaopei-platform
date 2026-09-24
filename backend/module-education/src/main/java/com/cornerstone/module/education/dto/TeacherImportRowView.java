package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 教师档案导入的单行结论，预览与落库共用同一个结构。
 *
 * @param rowNum Excel 里的实际行号（含表头，从 2 起），让操作员回到文件里能定位
 * @param teacherId 预览阶段只在本机构已有同编码时非空；导入后为新建或复用的档案ID
 */
@Schema(description = "教师档案导入单行结论")
public record TeacherImportRowView(
    @Schema(description = "Excel 行号") Integer rowNum,
    @Schema(description = "教师编码") String teacherCode,
    @Schema(description = "教师姓名") String teacherName,
    @Schema(description = "手机号") String phone,
    @Schema(description = "校区编码") String campusCode,
    @Schema(description = "性别字典值") String gender,
    @Schema(description = "用工类型字典值") String employmentType,
    @Schema(description = "入职日期") String hiredAt,
    @Schema(description = "状态") String status,
    @Schema(description = "校验是否通过") boolean valid,
    @Schema(description = "错误原因,多条用分号连接;通过为 null") String error,
    @Schema(description = "提示(如本机构已有同编码将跳过);无提示为 null") String notice,
    @Schema(description = "教师档案ID") Long teacherId
) {
}
