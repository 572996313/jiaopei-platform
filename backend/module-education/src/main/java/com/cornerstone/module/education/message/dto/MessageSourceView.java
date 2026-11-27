package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "数据源视图目录视图")
public record MessageSourceView(
    @Schema(description = "数据源ID") Long id,
    @Schema(description = "数据源代码") String sourceCode,
    @Schema(description = "数据源名称") String displayName,
    @Schema(description = "只读视图名") String viewName,
    @Schema(description = "说明") String description,
    @Schema(description = "视图真实列(实时读取)") List<String> columns,
    @Schema(description = "是否启用") Boolean enabled,
    @Schema(description = "最近一次列校验时间") LocalDateTime lastValidatedAt
) {
}
