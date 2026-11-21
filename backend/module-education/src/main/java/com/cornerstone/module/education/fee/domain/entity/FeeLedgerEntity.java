package com.cornerstone.module.education.fee.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_fee_ledger")
@Schema(description = "学员收费档案(台账头)")
public class FeeLedgerEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(创建时由班级带入,之后不可改)")
    private Long campusId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "学期ID")
    private Long termId;

    @Schema(description = "学员ID")
    private Long studentId;

    @Schema(description = "状态(active=在收,settled=已结清)", allowableValues = {"active", "settled"})
    private String status;

    @Schema(description = "备注(导出收费内容表的学员块备注)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
