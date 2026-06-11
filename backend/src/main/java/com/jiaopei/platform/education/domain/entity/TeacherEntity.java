package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_teacher")
public class TeacherEntity extends BaseEntity {

    private Long institutionId;
    private Long campusId;
    private String teacherCode;
    private String teacherName;
    private String phone;
    private String gender;
    private String title;
    private String employmentType;
    private String status;
    private LocalDate hiredAt;
    private String remark;
}
