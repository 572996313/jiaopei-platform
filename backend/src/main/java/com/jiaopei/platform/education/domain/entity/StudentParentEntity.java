package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_student_parent")
public class StudentParentEntity extends BaseEntity {

    private Long institutionId;
    private Long studentId;
    private String parentName;
    private String relationType;
    private String parentPhone;
    private String wechatNo;
    private Boolean isPrimary;
    private Boolean isEmergencyContact;
    private String status;
    private String remark;
}
