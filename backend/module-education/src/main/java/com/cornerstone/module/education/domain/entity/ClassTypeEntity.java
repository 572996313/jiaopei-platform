package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_type")
@Schema(description = "班级类型")
public class ClassTypeEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "类型编码")
    private String typeCode;

    @Schema(description = "类型名称(如 一对一/小班/大班)")
    private String typeName;

    @Schema(description = "授课方式字典(teaching_method:offline_small_class=线下小班,offline_large_class=线下大班,offline_one_to_one=线下1对1,online_small_class=线上小班,online_large_class=线上大班,online_one_to_one=线上1对1,offline=线下,online=线上,hybrid=混合)", allowableValues = {"offline_small_class", "offline_large_class", "offline_one_to_one", "online_small_class", "online_large_class", "online_one_to_one", "offline", "online", "hybrid"})
    private String teachingMethod;

    @Schema(description = "最小开班人数")
    private Integer minStudents;

    @Schema(description = "最大开班人数")
    private Integer maxStudents;

    @Schema(description = "状态(active=启用,disabled=停用)")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
