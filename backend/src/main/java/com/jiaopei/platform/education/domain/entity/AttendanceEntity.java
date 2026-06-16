package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_attendance")
public class AttendanceEntity extends BaseEntity {

    private Long institutionId;
    private Long classId;
    private Long lessonId;
    private Long studentId;
    private LocalDate attendanceDate;
    private String attendanceStatus;
    private String source;
    private String remark;
}
