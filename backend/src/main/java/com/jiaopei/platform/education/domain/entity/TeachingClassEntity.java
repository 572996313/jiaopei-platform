package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class")
public class TeachingClassEntity extends BaseEntity {

    private Long institutionId;
    private Long campusId;
    private Long termId;
    private Long courseId;
    private Long classTypeId;
    private String classCode;
    private String className;
    private Long teacherId;
    private Integer capacityLimit;
    private Integer currentStudents;
    private String status;
    private LocalDate openedAt;
    private String remark;
}
