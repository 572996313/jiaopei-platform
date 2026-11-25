package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * xlsx 批量导入的整批结果。
 *
 * <p>预览与落库共用同一个响应体：预览页要展示的逐行结论，和导入后要回报的结果是同一批字段，
 * 分两个 DTO 只会让前端两处渲染漂移。</p>
 */
@Schema(description = "xlsx 批量导入结果")
public record XlsxImportResult(
    @Schema(description = "读到的有效数据行数(整行为空的已剔除)") Integer total,
    @Schema(description = "校验通过、可导入的行数") Integer validCount,
    @Schema(description = "校验失败的行数;大于 0 时整批不落库") Integer errorCount,
    @Schema(description = "本次实际新建的行数;预览阶段为 0") Integer createdCount,
    @Schema(description = "跳过未建的行数(已存在);预览阶段为 0") Integer skippedCount,
    @Schema(description = "逐行明细") List<XlsxImportRowView> rows
) {

    /** 预览态：还没有落库，created/skipped 自然为 0 */
    public static XlsxImportResult preview(List<XlsxImportRowView> rows) {
        int validCount = (int) rows.stream().filter(XlsxImportRowView::valid).count();
        return new XlsxImportResult(rows.size(), validCount, rows.size() - validCount, 0, 0, rows);
    }

    public static XlsxImportResult committed(int createdCount, int skippedCount, List<XlsxImportRowView> rows) {
        int validCount = (int) rows.stream().filter(XlsxImportRowView::valid).count();
        return new XlsxImportResult(rows.size(), validCount, rows.size() - validCount,
            createdCount, skippedCount, rows);
    }
}
