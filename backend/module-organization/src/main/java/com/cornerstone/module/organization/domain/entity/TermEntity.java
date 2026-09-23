package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_term")
public class TermEntity extends BaseEntity {

    private Long institutionId;
    private String termCode;
    private String termName;
    private String academicYear;
    @Schema(description = "学季字典(term_season:spring=春季,summer=夏季,autumn=秋季,winter=冬季)", allowableValues = {"spring", "summer", "autumn", "winter"})
    private String season;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate enrollmentStartDate;
    private LocalDate enrollmentEndDate;
    private String status;

    @TableField("is_default")
    private Boolean defaultTerm;

    private String remark;
}
