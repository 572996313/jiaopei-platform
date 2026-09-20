package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_time_slot_template")
@Schema(description = "时段模板(节次参数快照,套用时展开为时间段行)")
public class TimeSlotTemplateEntity extends BaseEntity {

    @Schema(description = "机构ID")
    private Long institutionId;

    @Schema(description = "校区ID,空表示机构级模板")
    private Long campusId;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "节次参数快照(JSON字符串:课程时长/课间/各时段节数/大课间/套用星期)")
    private String configJson;

    @Schema(description = "状态(active=启用,inactive=停用)")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
