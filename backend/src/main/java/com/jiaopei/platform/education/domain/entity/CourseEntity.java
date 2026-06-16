package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_course")
public class CourseEntity extends BaseEntity {

    private Long institutionId;
    private Long categoryId;
    private String courseCode;
    private String courseName;
    private String subjectName;
    private String teachingMethod;
    private String status;
    private String remark;
}
