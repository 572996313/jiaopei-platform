package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "教室类型字典(room_type:normal=标准教室,piano=琴房,lab=实验室,dance=舞蹈教室,art=美术教室)", allowableValues = {"normal", "piano", "lab", "dance", "art"})
    private String roomType;
    private String floorNo;
    private Integer seatCapacity;
    private BigDecimal areaSqm;
    private String equipmentTags;
    private String status;
    private Boolean allowScheduling;
    private String remark;
}
