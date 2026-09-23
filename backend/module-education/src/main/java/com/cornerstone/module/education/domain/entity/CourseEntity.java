package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_course")
@Schema(description = "课程")
public class CourseEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "课程分类ID")
    private Long categoryId;

    @Schema(description = "课程编码")
    private String courseCode;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "学科名称(如 数学/英语)")
    private String subjectName;

    @Schema(description = "授课方式字典(teaching_method:offline_small_class=线下小班,offline_large_class=线下大班,offline_one_to_one=线下1对1,online_small_class=线上小班,online_large_class=线上大班,online_one_to_one=线上1对1,offline=线下,online=线上,hybrid=混合)", allowableValues = {"offline_small_class", "offline_large_class", "offline_one_to_one", "online_small_class", "online_large_class", "online_one_to_one", "offline", "online", "hybrid"})
    private String teachingMethod;

    @Schema(description = "状态(active=启用,disabled=停用)")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
