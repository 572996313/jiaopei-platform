package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 编号规则列表的一行：一条已登记的资源 + 它的规则（可能还没配置）+ 下一个号预览。
 *
 * <p>列表以代码登记的资源清单为准，不是以表里的行为准——没登记的资源改不了，配了的资源不会漏。
 * 没配置过的资源同样出现在清单里（enabled=false、configured=false），让运维看得见"有这条能力但还没开"。</p>
 */
@Schema(description = "编号生成规则视图")
public record CodeRuleView(
    @Schema(description = "规则行ID;该资源还没配置过时为空") Long id,
    @Schema(description = "编码资源码") String resourceCode,
    @Schema(description = "中文展示名,来自模块登记") String label,
    @Schema(description = "是否已经写过规则行(false=从未配置,展示的是代码登记的默认格式)") boolean configured,
    @Schema(description = "是否启用自动编号") boolean enabled,
    @Schema(description = "生效前缀") String prefix,
    @Schema(description = "生效分隔符,空串表示各段直接相连") String separator,
    @Schema(description = "生效日期段格式,空串表示不带日期段") String datePattern,
    @Schema(description = "生效序号位数") int serialLength,
    @Schema(description = "登记默认前缀,供表单首次预填") String defaultPrefix,
    @Schema(description = "登记默认分隔符,供表单首次预填") String defaultSeparator,
    @Schema(description = "登记默认日期段格式,供表单首次预填") String defaultDatePattern,
    @Schema(description = "登记默认序号位数,供表单首次预填") int defaultSerialLength,
    @Schema(description = "下一个号预览;未启用时为空。只是此刻算出来的建议值,保存时以保存侧现算为准") String previewCode,
    @Schema(description = "备注") String remark
) {
}
