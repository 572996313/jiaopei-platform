package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TermCreateRequest(
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @JsonAlias({"code", "term_code"})
    @NotBlank(message = "学期编码不能为空")
    String termCode,
    @JsonAlias({"name", "term_name"})
    @NotBlank(message = "学期名称不能为空")
    String termName,
    @JsonAlias("academic_year")
    @NotBlank(message = "学年不能为空")
    String academicYear,
    String season,
    @JsonAlias({"start_date", "beginDate"})
    @NotNull(message = "开始日期不能为空")
    LocalDate startDate,
    @JsonAlias({"end_date", "finishDate"})
    @NotNull(message = "结束日期不能为空")
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
