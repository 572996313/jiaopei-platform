package com.cornerstone.module.education.message.dto;

import com.cornerstone.module.education.message.support.MessageCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "事件类型配置视图")
public record MessageEventTypeView(
    @Schema(description = "类型ID") Long id,
    @Schema(description = "类型码") String typeCode,
    @Schema(description = "类型名称") String typeName,
    @Schema(description = "分类字典(message_category)") String category,
    @Schema(description = "产生方式(scan=巡检,manual=人工)") String sourceMode,
    @Schema(description = "数据源代码") String sourceCode,
    @Schema(description = "数据源只读视图名") String sourceViewName,
    @Schema(description = "过滤条件") List<MessageCondition> conditions,
    @Schema(description = "标题模板") String titleTemplate,
    @Schema(description = "正文模板") String bodyTemplate,
    @Schema(description = "深链模板") String actionTemplate,
    @Schema(description = "去重键模板") String dedupeTemplate,
    @Schema(description = "级别") String level,
    @Schema(description = "可见端") String visibility,
    @Schema(description = "巡检间隔(分钟)") Integer intervalMinutes,
    @Schema(description = "保留天数") Integer retentionDays,
    @Schema(description = "是否可手动新建") Boolean creatable,
    @Schema(description = "是否启用") Boolean enabled,
    @Schema(description = "最近一次巡检时间") LocalDateTime lastRunAt,
    @Schema(description = "最近一次巡检命中行数") Integer lastRunCount,
    @Schema(description = "最近一次巡检失败原因") String lastError,
    @Schema(description = "排序号") Integer sortNo
) {
}
