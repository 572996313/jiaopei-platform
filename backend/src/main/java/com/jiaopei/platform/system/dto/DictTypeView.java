package com.jiaopei.platform.system.dto;

public record DictTypeView(
    Long id,
    String typeCode,
    String typeName,
    String status,
    String remark
) {
}
