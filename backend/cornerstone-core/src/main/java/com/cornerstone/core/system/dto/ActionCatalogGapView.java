package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "按钮授权目录缺口：代码登记的动作在数据库里找不到页面或授权节点")
public record ActionCatalogGapView(
    @Schema(description = "动作业务键") String actionKey,
    @Schema(description = "所属页面编码") String pageMenuCode,
    @Schema(description = "承载授权的节点编码") String grantMenuCode,
    @Schema(description = "缺口说明") String message
) {
}
