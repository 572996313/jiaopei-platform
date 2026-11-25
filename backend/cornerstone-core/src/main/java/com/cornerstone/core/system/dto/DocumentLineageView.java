package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "业务单据链路：各模块声明的单据节点与生成/汇总/核销关系")
public record DocumentLineageView(
    @Schema(description = "生成时间") LocalDateTime generatedAt,
    @Schema(description = "口径说明") String note,
    @Schema(description = "单据数量") int documentCount,
    @Schema(description = "关系数量") int edgeCount,
    @Schema(description = "待确认项数量（节点 + 关系）") int pendingCount,
    @Schema(description = "单据节点") List<DocumentLineageNodeView> documents,
    @Schema(description = "单据关系") List<DocumentLineageEdgeView> edges
) {
}
