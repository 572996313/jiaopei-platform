package com.cornerstone.module.education.fee.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_fee_item")
@Schema(description = "收费项目")
public class FeeItemEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "收费项目编码")
    private String itemCode;

    @Schema(description = "收费项目名称(保教费/伙食费/延时费…)")
    private String itemName;

    @Schema(description = "计费方式(monthly=按月,daily=按天)", allowableValues = {"monthly", "daily"})
    private String billingMode;

    @Schema(description = "排序号(决定导出矩阵块内的项目行序)")
    private Integer sortNo;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}
