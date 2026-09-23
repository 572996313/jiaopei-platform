package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record TermUpdateRequest(
    @JsonAlias({"code", "term_code"})
    String termCode,
    @JsonAlias({"name", "term_name"})
    String termName,
    @JsonAlias("academic_year")
    String academicYear,
    @Schema(description = "学季字典(term_season:spring=春季,summer=夏季,autumn=秋季,winter=冬季)", allowableValues = {"spring", "summer", "autumn", "winter"})
    String season,
    @JsonAlias({"start_date", "beginDate"})
    LocalDate startDate,
    @JsonAlias({"end_date", "finishDate"})
    LocalDate endDate,
    @JsonAlias("enrollment_start_date")
    LocalDate enrollmentStartDate,
    @JsonAlias("enrollment_end_date")
    LocalDate enrollmentEndDate,
    String status,
    @JsonAlias({"defaultTerm", "is_default"})
    Boolean defaultTerm,
    String remark
) {
}