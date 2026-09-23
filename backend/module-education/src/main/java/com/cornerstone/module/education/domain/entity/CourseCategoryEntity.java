package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_course_category")
@Schema(description = "课程分类")
public class CourseCategoryEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "分类编码")
    private String categoryCode;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "状态(active=启用,disabled=停用)")
    private String status;

    @Schema(description = "排序号(越小越靠前)")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
