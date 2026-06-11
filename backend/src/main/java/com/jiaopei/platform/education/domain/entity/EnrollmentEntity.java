package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_enrollment")
public class EnrollmentEntity extends BaseEntity {

    private Long institutionId;
    private Long studentId;
    private Long classId;
    private String enrollmentNo;
    private String channel;
    private String status;
    private LocalDate enrolledAt;
    private String remark;
}
