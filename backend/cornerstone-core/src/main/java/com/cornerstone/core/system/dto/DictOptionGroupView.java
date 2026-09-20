package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DictOptionGroupView(
    @Schema(description = "字典类型编码", example = "room_type")
    String typeCode,
    @Schema(description = "字典类型名称", example = "教室类型")
    String typeName,
    @Schema(description = "启用的字典条目列表,按 sortNo 升序")
    List<DictItemView> items
) {
}
