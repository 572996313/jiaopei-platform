package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 排课完成度报表：行=班级，列=自然周，格子给出「应排/已排/已上/缺口」。
 *
 * <p>应排（分母）一律来自该班启用中的排课计划展开，不是课时目标——系统里没有「班级应上多少课」的独立口径，
 * 计划就是操作员认定的应排。因此没建计划的班级不会出现在报表里。
 */
@Schema(description = "排课完成度报表响应(行=班级,列=自然周,应排取自启用中的排课计划)")
public record ScheduleCompletenessResponse(
    @Schema(description = "统计区间内对齐后的起始周一")
    LocalDate weekFrom,
    @Schema(description = "统计区间内对齐后的最后一个周日")
    LocalDate weekTo,
    @Schema(description = "周列定义,与每行 cells 按下标一一对应")
    List<WeekColumn> weeks,
    @Schema(description = "班级行,按班级名称排序")
    List<ClassRow> classes,
    @Schema(description = "整表汇总")
    Summary summary
) {
    @Schema(description = "周列")
    public record WeekColumn(
        @Schema(description = "列序号(1 起,报表区间内自然周顺序)")
        int index,
        @Schema(description = "本周周一")
        LocalDate weekStart,
        @Schema(description = "本周周日")
        LocalDate weekEnd
    ) {
    }

    @Schema(description = "班级行")
    public record ClassRow(
        @Schema(description = "班级 ID")
        Long classId,
        @Schema(description = "班级名称")
        String className,
        @Schema(description = "校区 ID")
        Long campusId,
        @Schema(description = "学期 ID")
        Long termId,
        @Schema(description = "参与统计的启用中计划数")
        int planCount,
        @Schema(description = "与 weeks 等长的格子列表")
        List<WeekCell> cells,
        @Schema(description = "该行合计")
        RowSummary summary
    ) {
    }

    @Schema(description = "班级×周格子")
    public record WeekCell(
        @Schema(description = "该周是否被任一计划区间覆盖;false 表示计划没排到这一周,数字无意义")
        boolean covered,
        @Schema(description = "应排节数(计划展开且未命中跳过日期)")
        int expected,
        @Schema(description = "已生成课节数(含已停课;按计划键匹配)")
        int generated,
        @Schema(description = "已完成课节数")
        int completed,
        @Schema(description = "已停课课节数")
        int cancelled,
        @Schema(description = "计划外课节数(手工加排、补课等落在计划键之外)")
        int extra,
        @Schema(description = "缺口明细:应排但没有生成课次的日期与节次")
        List<Gap> gaps
    ) {
    }

    @Schema(description = "缺口条目(计划里有、课次表里没有)")
    public record Gap(
        @Schema(description = "上课日期")
        LocalDate lessonDate,
        @Schema(description = "星期(1=周一,7=周日)")
        Integer weekday,
        @Schema(description = "时段 ID")
        Long timeSlotId,
        @Schema(description = "时段名称(如 上1/下2)")
        String slotName
    ) {
    }

    @Schema(description = "行合计")
    public record RowSummary(
        @Schema(description = "应排节数合计")
        int expected,
        @Schema(description = "已生成课节合计")
        int generated,
        @Schema(description = "已完成课节合计")
        int completed,
        @Schema(description = "缺口合计")
        int missing,
        @Schema(description = "计划外课节合计")
        int extra
    ) {
    }

    @Schema(description = "整表汇总")
    public record Summary(
        @Schema(description = "参与统计的班级数")
        int classCount,
        @Schema(description = "应排节数合计")
        int expected,
        @Schema(description = "已生成课节合计")
        int generated,
        @Schema(description = "已完成课节合计")
        int completed,
        @Schema(description = "缺口合计")
        int missing,
        @Schema(description = "计划外课节合计")
        int extra,
        @Schema(description = "生成进度百分比(已生成/应排,0-100 取整)")
        int generatedPercent,
        @Schema(description = "上课进度百分比(已完成/应排,0-100 取整)")
        int completedPercent
    ) {
    }
}
