package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_student")
public class StudentEntity extends BaseEntity {

    private Long institutionId;
    private Long campusId;
    private String studentNo;
    private String studentName;
    private String gender;
    private LocalDate birthday;
    private String guardianName;
    private String guardianPhone;
    private String status;
    private LocalDate enrolledAt;
    private String remark;
}
