package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "业务单据关系")
public record DocumentLineageEdgeView(
    @Schema(description = "来源单据编码") String from,
    @Schema(description = "目标单据编码") String to,
    @Schema(description = "关系类型（生成/汇总/重算/核销等）") String relation,
    @Schema(description = "关联字段") List<String> linkFields,
    @Schema(description = "触发入口中文名") String triggerLabel,
    @Schema(description = "触发接口，格式 METHOD /path") String triggerEndpoint,
    @Schema(description = "关系说明") String description,
    @Schema(description = "业务口径是否已确认") boolean confirmed,
    @Schema(description = "是否为模块显式声明的业务关系；false 表示数据库外键自动推导") boolean declared
) {
}
