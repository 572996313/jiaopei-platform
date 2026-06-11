package com.jiaopei.platform.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_time_slot")
public class TimeSlotEntity extends BaseEntity {

    private Long institutionId;
    private Long campusId;
    private String slotCode;
    private String slotName;
    private Integer weekday;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private String slotType;
    private String applicableScene;
    private String status;
    private Integer sortNo;
    private String remark;
}
