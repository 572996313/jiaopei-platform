package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 排课完成度报表：行=班级，列=自然周，格子给出「应排/已排/已上/缺口」。
 *
 * <p>分母有两套口径，按行标注在 {@code denominatorSource}：
 * <ul>
 *   <li>{@code weekly_target} — 班级设了「每周应上节数」({@code jp_class.weekly_lessons})，
 *       应排 = 该值 × 覆盖周数。这是自动排课要对齐的目标，与有没有建计划无关。</li>
 *   <li>{@code plan_expand} — 没设目标时回落到"该班启用中的排课计划展开"，即操作员认定的应排。</li>
 * </ul>
 * 两种口径都只统计被计划区间覆盖的周（没建计划的班级与没排到的周仍不出数），
 * 因此同一张表里可能两种分母并存，比数之前先看行上的口径标签。缺口明细(gaps)始终来自计划展开：
 * 按目标计数时，目标比计划格子多出来的那部分只知道缺几节、说不出缺在哪个格子。
 */
@Schema(description = "排课完成度报表响应(行=班级,列=自然周,分母=班级课时目标,未设目标时回落计划展开)")
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
        @Schema(description = "分母口径(weekly_target=按班级每周应上节数,plan_expand=按计划展开)",
                allowableValues = {"weekly_target", "plan_expand"})
        String denominatorSource,
        @Schema(description = "每周应上节数(班级课时目标;未设定时为 null,此时分母走计划展开)")
        Integer targetLessons,
        @Schema(description = "整班总课时(班级未设时为空;只用于课时进度,不参与应排/缺口计算)",
                example = "40")
        Integer totalLessons,
        @Schema(description = "已上课节数(上课日期早于今天且未停课的全部课次。不按计划格子匹配,"
            + "与格子里的「已完成」口径不同：计划外加的课、补课另排的那一格这里算、格子里不算)")
        Integer heldLessons,
        @Schema(description = "预计结课日(按近四周实际每周上课节数推算剩余节数要几周,只到周粒度;算不出时为空)")
        LocalDate estimatedFinishDate,
        @Schema(description = "该班启用中排课计划的最晚结束日期(与预计结课日对比才知道按现在的节奏上不上得完)")
        LocalDate planEndDate,
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
        @Schema(description = "本周缺口数(应排-已生成,不小于0;按课时目标统计时可能多于 gaps 条数)")
        int missing,
        @Schema(description = "已完成课节数")
        int completed,
        @Schema(description = "已停课课节数")
        int cancelled,
        @Schema(description = "计划外课节数(手工加排、补课等落在计划键之外)")
        int extra,
        @Schema(description = "缺口明细:计划里有但没有生成课次的日期与节次(按课时目标统计时可能少于 missing)")
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
