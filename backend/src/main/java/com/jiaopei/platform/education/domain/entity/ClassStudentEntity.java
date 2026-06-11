package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_student")
public class ClassStudentEntity extends BaseEntity {

    private Long institutionId;
    private Long classId;
    private Long studentId;
    private String status;
    private LocalDate joinedAt;
    private LocalDate leftAt;
    private String remark;
}
