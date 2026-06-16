package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_course_category")
public class CourseCategoryEntity extends BaseEntity {

    private Long institutionId;
    private String categoryCode;
    private String categoryName;
    private String status;
    private Integer sortOrder;
    private String remark;
}
