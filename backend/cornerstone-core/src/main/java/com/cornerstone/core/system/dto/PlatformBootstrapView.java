package com.cornerstone.core.system.dto;

import java.util.List;

public record PlatformBootstrapView(
    String application,
    String version,
    List<ModuleSummaryView> modules,
    List<String> nextSteps
) {
}
