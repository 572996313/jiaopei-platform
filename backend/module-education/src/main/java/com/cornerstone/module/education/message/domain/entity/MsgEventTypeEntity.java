package com.cornerstone.module.education.message.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事件类型目录。
 *
 * <p>运维在页面上加类型、调阈值（condition_json）、改文案模板、调级别/可见性/间隔，
 * 都不需要改代码；只有出现新的数据形状才需要开发补只读视图。</p>
 *
 * <p>condition_json 只允许 {@code [{column,op,value}]} 白名单比较，不是 SQL 片段；
 * 模板只做 {@code ${列名}} 纯文本替换。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_event_type")
@Schema(description = "事件类型目录(运维配置)")
public class MsgEventTypeEntity extends BaseEntity {

    @Schema(description = "类型码(代码/配置判定,不入字典)")
    private String typeCode;

    @Schema(description = "类型名称")
    private String typeName;

    @Schema(description = "分类字典(message_category)")
    private String category;

    @Schema(description = "产生方式(scan=配置化巡检,manual=人工,api=接口发布;代码判定,不入字典)")
    private String sourceMode;

    @Schema(description = "scan 时指向 jp_msg_source.source_code")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceCode;

    @Schema(description = "过滤条件 JSON:[{column,op,value}]")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String conditionJson;

    @Schema(description = "标题模板")
    private String titleTemplate;

    @Schema(description = "正文模板")
    private String bodyTemplate;

    @Schema(description = "深链模板")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String actionTemplate;

    @Schema(description = "去重键模板")
    private String dedupeTemplate;

    @Schema(description = "级别(info/warning/critical)")
    private String level;

    @Schema(description = "可见端(internal/parent/student)")
    private String visibility;

    @Schema(description = "巡检间隔(分钟)")
    private Integer intervalMinutes;

    @Schema(description = "保留天数")
    private Integer retentionDays;

    @Schema(description = "是否可在新建事项里手选")
    private Boolean creatable;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "最近一次巡检时间")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDateTime lastRunAt;

    @Schema(description = "最近一次巡检命中行数")
    private Integer lastRunCount;

    @Schema(description = "最近一次巡检失败原因(成功为NULL)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;

    @Schema(description = "排序号")
    private Integer sortNo;
}
