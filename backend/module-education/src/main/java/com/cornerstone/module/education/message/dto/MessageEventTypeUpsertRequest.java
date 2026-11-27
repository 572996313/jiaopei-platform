package com.cornerstone.module.education.message.dto;

import com.cornerstone.module.education.message.support.MessageCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "新建/编辑事件类型请求")
public class MessageEventTypeUpsertRequest {

    @Schema(description = "类型码(小写字母/数字/下划线，新建后不可改)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类型码不能为空")
    @Size(max = 64, message = "类型码长度不能超过 64")
    @Pattern(regexp = "[a-z][a-z0-9_]*", message = "类型码只能由小写字母、数字、下划线组成，且以字母开头")
    private String typeCode;

    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类型名称不能为空")
    @Size(max = 64, message = "类型名称长度不能超过 64")
    private String typeName;

    @Schema(description = "分类字典(message_category)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类不能为空")
    private String category;

    @Schema(description = "产生方式(scan=巡检,manual=人工)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "产生方式不能为空")
    @Pattern(regexp = "scan|manual", message = "产生方式只能是 scan 或 manual")
    private String sourceMode;

    @Schema(description = "scan 时必填：数据源代码")
    @Size(max = 64, message = "数据源代码长度不能超过 64")
    private String sourceCode;

    @Schema(description = "过滤条件；scan 时按视图列做白名单比较")
    @Size(max = 100, message = "过滤条件不能超过 100 条")
    private List<@Valid MessageCondition> conditions;

    @Schema(description = "标题模板(${列名})", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题模板不能为空")
    @Size(max = 200, message = "标题模板长度不能超过 200")
    private String titleTemplate;

    @Schema(description = "正文模板(${列名})", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "正文模板不能为空(可为空串)")
    @Size(max = 5000, message = "正文模板长度不能超过 5000")
    private String bodyTemplate;

    @Schema(description = "深链模板(${列名})，渲染后按白名单校验")
    @Size(max = 500, message = "深链模板长度不能超过 500")
    private String actionTemplate;

    @Schema(description = "去重键模板(${列名})", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "去重键模板不能为空(人工类型可为空串)")
    @Size(max = 300, message = "去重键模板长度不能超过 300")
    private String dedupeTemplate;

    @Schema(description = "级别(info/warning/critical)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "级别不能为空")
    @Pattern(regexp = "info|warning|critical", message = "级别只能是 info/warning/critical")
    private String level;

    @Schema(description = "可见端(internal/parent/student)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "可见端不能为空")
    @Pattern(regexp = "internal|parent|student", message = "可见端只能是 internal/parent/student")
    private String visibility;

    @Schema(description = "巡检间隔(分钟)")
    @Min(value = 1, message = "巡检间隔不能小于 1 分钟")
    @Max(value = 1440, message = "巡检间隔不能超过 1440 分钟")
    private Integer intervalMinutes;

    @Schema(description = "保留天数")
    @Min(value = 1, message = "保留天数不能小于 1")
    @Max(value = 3650, message = "保留天数不能超过 3650")
    private Integer retentionDays;

    @Schema(description = "是否可在新建事项里手选")
    private Boolean creatable;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序号")
    private Integer sortNo;
}
