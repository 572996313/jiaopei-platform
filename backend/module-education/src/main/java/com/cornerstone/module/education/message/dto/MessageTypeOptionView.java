package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "事件类型选项(下拉/筛选用)")
public record MessageTypeOptionView(
    @Schema(description = "类型码") String typeCode,
    @Schema(description = "类型名称") String typeName,
    @Schema(description = "分类字典(message_category)") String category,
    @Schema(description = "默认级别") String level,
    @Schema(description = "产生方式(scan/manual)") String sourceMode,
    @Schema(description = "是否可手动新建") Boolean creatable
) {
}
