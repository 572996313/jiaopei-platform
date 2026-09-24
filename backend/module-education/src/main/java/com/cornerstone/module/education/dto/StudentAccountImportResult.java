package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "账号批量导入结果")
public record StudentAccountImportResult(
    @Schema(description = "数据行总数") Integer total,

    @Schema(description = "通过校验的行数") Integer validCount,

    @Schema(description = "存在错误的行数,大于 0 时不允许落库") Integer errorCount,

    @Schema(description = "本次新建的登录账号数(同一手机号的多行只建一个)") Integer createdCount,

    @Schema(description = "本次建立的账号-学员绑定数") Integer boundCount,

    @Schema(description = "跳过或补绑定的行数(账号已存在或绑定已存在)") Integer skippedCount,

    @Schema(description = "逐行明细") List<StudentAccountImportRowView> rows
) {
    public static StudentAccountImportResult of(List<StudentAccountImportRowView> rows) {
        int errorCount = (int) rows.stream().filter(row -> !Boolean.TRUE.equals(row.valid())).count();
        return new StudentAccountImportResult(rows.size(), rows.size() - errorCount, errorCount,
            0, 0, 0, rows);
    }
}
