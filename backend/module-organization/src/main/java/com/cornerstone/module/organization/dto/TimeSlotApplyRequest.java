package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "时段模板套用(批量展开保存)请求")
public record TimeSlotApplyRequest(
    @Schema(description = "节次参数快照,随本次保存回写模板")
    @JsonAlias("config_json")
    JsonNode config,
    @Schema(description = "网格中最终确认的全部节次,按(星期,编码)与存量匹配:已有则更新,新增则插入,移除则删除或停用;传空数组即清空该模板下的全部节次",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "节次列表不能为空")
    List<TimeSlotApplySlot> slots
) {
}
