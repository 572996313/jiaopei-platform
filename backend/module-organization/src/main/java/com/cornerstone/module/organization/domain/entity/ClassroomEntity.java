package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_classroom")
public class ClassroomEntity extends BaseEntity {

    private Long campusId;
    private String classroomCode;
    private String classroomName;
    private String roomType;
    private String floorNo;
    private Integer seatCapacity;
    private BigDecimal areaSqm;
    private String equipmentTags;
    private String status;
    private Boolean allowScheduling;
    private String remark;
}
