package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_opening_rule")
public class ClassOpeningRuleEntity extends BaseEntity {

    private Long institutionId;
    private Long campusId;
    private Long courseId;
    private Long classTypeId;
    private Long teacherId;
    private String ruleName;
    private Integer minStudents;
    private Integer maxStudents;
    private Boolean allowPreOpen;
    private String impactScope;
    private Integer priorityNo;
    private String status;
    private String remark;
}
