package com.jiaopei.platform.system.dto;

public record ModuleSummaryView(
    String code,
    String name,
    boolean enabled,
    String status
) {
}
