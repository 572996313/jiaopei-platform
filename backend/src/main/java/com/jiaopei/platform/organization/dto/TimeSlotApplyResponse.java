package com.jiaopei.platform.organization.dto;

import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "时段模板套用结果")
public record TimeSlotApplyResponse(
    @Schema(description = "新增时间段数量") Integer createdCount,
    @Schema(description = "更新时间段数量") Integer updatedCount,
    @Schema(description = "移除(物理删除)数量") Integer removedCount,
    @Schema(description = "因被排课引用而改为停用的数量") Integer deactivatedCount,
    @Schema(description = "套用后的全部时间段明细") List<TimeSlotEntity> slots
) {
}
