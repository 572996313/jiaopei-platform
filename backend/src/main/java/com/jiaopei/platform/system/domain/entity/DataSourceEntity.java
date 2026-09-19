package com.jiaopei.platform.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 选项数据源配置:把页面下拉/多选的选项来源收敛为一条可配置记录。
 * 前端按 sourceCode 消费,改接口路径只需改这一条配置,无需改代码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_data_source")
@Schema(description = "选项数据源配置")
public class DataSourceEntity extends BaseEntity {

    @Schema(description = "数据源编码,唯一标识,如 institutions/roles", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceCode;

    @Schema(description = "数据源名称,如 机构列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceName;

    @Schema(description = "接口路径,如 /organizations/institutions", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endpoint;

    @Schema(description = "请求方法,默认 GET", allowableValues = {"GET", "POST"})
    private String method;

    @Schema(description = "选项值字段,缺省为 id")
    private String valueKey;

    @Schema(description = "选项标签字段,如 orgName;支持嵌套,如 user.name")
    private String labelKey;

    @Schema(description = "请求参数定义 JSON 数组,如 [{\"name\":\"institutionId\",\"required\":false}]")
    private String paramDefs;

    @Schema(description = "数据源类型(api=接口数据,dict=字典)", allowableValues = {"api", "dict"})
    private String sourceType;

    @Schema(description = "source_type=dict 时的字典类型编码")
    private String dictType;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}