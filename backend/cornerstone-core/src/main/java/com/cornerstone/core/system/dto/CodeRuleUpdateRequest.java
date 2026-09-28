package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 编号规则保存请求：一个资源一条规则，按资源码 upsert（没有新增/删除，所以也不接收ID）。
 *
 * <p>分隔符与日期段允许传空串，且空串是<b>有语义的</b>取值（各段直接相连 / 不带日期段即全局累加），
 * 不是"回落默认"。默认值只在维护页第一次打开表单时用来预填。</p>
 */
@Schema(description = "编号生成规则保存请求")
public record CodeRuleUpdateRequest(
    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否启用自动编号(false=该页编号仍必填、仍需手填)", requiredMode = Schema.RequiredMode.REQUIRED)
    Boolean enabled,

    @NotNull(message = "编号前缀不能为空") @Size(max = 16, message = "编号前缀长度不能超过16个字符")
    @Schema(description = "编号前缀,如 STU", requiredMode = Schema.RequiredMode.REQUIRED)
    String prefix,

    @Size(max = 1, message = "分隔符只能是一个字符")
    @Schema(description = "段间分隔符,可选 - _ . 或空串(各段直接相连)") String separator,

    @Size(max = 16, message = "日期段格式长度不能超过16个字符")
    @Schema(description = "日期段格式,空串表示不带日期段;可选 yyyyMMdd/yyMMdd/yyyyMM/yyMM/yyyy/yy") String datePattern,

    @NotNull(message = "序号位数不能为空") @Min(value = 1, message = "序号位数不能小于1") @Max(value = 10, message = "序号位数不能大于10")
    @Schema(description = "序号位数,不足补零;实际号超出位数自动加宽", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer serialLength,

    @Size(max = 500, message = "备注长度不能超过500个字符")
    @Schema(description = "备注") String remark
) {
}
