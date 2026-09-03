package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_attendance")
@Schema(description = "出勤记录")
public class AttendanceEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "课次ID")
    private Long lessonId;

    @Schema(description = "学员ID")
    private Long studentId;

    @Schema(description = "出勤日期")
    private LocalDate attendanceDate;

    @Schema(description = "出勤状态(PRESENT=出勤,ABSENT=缺勤,LATE=迟到,LEAVE=请假,EARLY_LEAVE=早退)", allowableValues = {"PRESENT", "ABSENT", "LATE", "LEAVE", "EARLY_LEAVE"})
    private String attendanceStatus;

    @Schema(description = "数据来源(MANUAL=手动,AUTO=自动,IMPORT=导入)")
    private String source;

    @Schema(description = "备注")
    private String remark;
}
