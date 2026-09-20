package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_opening_rule")
@Schema(description = "开班规则")
public class ClassOpeningRuleEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "课程ID")
    private Long courseId;

    @Schema(description = "班级类型ID")
    private Long classTypeId;

    @Schema(description = "默认主讲教师ID")
    private Long teacherId;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "最小开班人数")
    private Integer minStudents;

    @Schema(description = "最大开班人数")
    private Integer maxStudents;

    @Schema(description = "是否允许预开班")
    private Boolean allowPreOpen;

    @Schema(description = "影响范围(如 ALL / COURSE / CLASS_TYPE)")
    private String impactScope;

    @Schema(description = "优先级(数值越小优先级越高)")
    private Integer priorityNo;

    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)", allowableValues = {"ENABLED", "DISABLED"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}
