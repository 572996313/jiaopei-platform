package com.jiaopei.platform.organization.dto;

import java.util.List;

public record OrganizationBootstrapView(
    boolean enabled,
    String status,
    List<String> capabilities,
    List<String> nextSteps
) {
}
