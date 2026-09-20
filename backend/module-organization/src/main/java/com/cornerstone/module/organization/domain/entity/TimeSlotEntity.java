package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_time_slot")
@Schema(description = "时间段(每星期每节次一行,由时段模板批量展开或手工录入)")
public class TimeSlotEntity extends BaseEntity {

    @Schema(description = "机构ID")
    private Long institutionId;

    @Schema(description = "校区ID,空表示机构级")
    private Long campusId;

    @Schema(description = "时段编码(同机构+校区+星期内唯一)")
    private String slotCode;

    @Schema(description = "时段名称(如 早自习/上1/下2/晚1)")
    private String slotName;

    @Schema(description = "星期几(1=周一,7=周日)")
    private Integer weekday;

    @Schema(description = "开始时间")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    private LocalTime endTime;

    @Schema(description = "时长(分钟),必须等于起止时间差")
    private Integer durationMinutes;

    @Schema(description = "时段类型(morning_study=早自习,teaching=授课,break=课间休息,self_study=晚自习)",
            allowableValues = {"morning_study", "teaching", "break", "self_study"})
    private String slotType;

    @Schema(description = "适用场景")
    private String applicableScene;

    @Schema(description = "状态(active=启用,inactive=停用)")
    private String status;

    @Schema(description = "来源时段模板ID,空表示手工录入")
    private Long templateId;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "备注")
    private String remark;
}
