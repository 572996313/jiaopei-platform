package com.jiaopei.platform.system.dto;

public record DictItemView(
    Long id,
    Long typeId,
    String itemCode,
    String itemName,
    String itemValue,
    Integer sortNo,
    String status,
    String remark
) {
}
