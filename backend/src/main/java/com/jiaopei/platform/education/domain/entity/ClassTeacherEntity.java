package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_teacher")
public class ClassTeacherEntity extends BaseEntity {

    private Long institutionId;
    private Long classId;
    private Long teacherId;
    private String teacherRole;
    private Boolean isPrimary;
    private LocalDate joinedAt;
    private LocalDate leftAt;
    private String status;
    private String remark;
}
