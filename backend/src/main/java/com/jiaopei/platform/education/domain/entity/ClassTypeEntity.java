package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_type")
public class ClassTypeEntity extends BaseEntity {

    private Long institutionId;
    private String typeCode;
    private String typeName;
    private String teachingMethod;
    private Integer minStudents;
    private Integer maxStudents;
    private String status;
    private String remark;
}
