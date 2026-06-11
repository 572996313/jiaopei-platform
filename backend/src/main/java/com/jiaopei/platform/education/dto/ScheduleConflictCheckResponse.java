package com.jiaopei.platform.education.dto;

import java.util.List;

public record ScheduleConflictCheckResponse(
    Boolean conflict,
    List<String> conflictTypes,
    List<String> messages
) {
}
