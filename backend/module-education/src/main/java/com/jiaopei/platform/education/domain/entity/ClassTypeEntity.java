package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_type")
@Schema(description = "班级类型")
public class ClassTypeEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "类型编码")
    private String typeCode;

    @Schema(description = "类型名称(如 一对一/小班/大班)")
    private String typeName;

    @Schema(description = "授课方式(ONLINE=线上,OFFLINE=线下,HYBRID=混合)")
    private String teachingMethod;

    @Schema(description = "最小开班人数")
    private Integer minStudents;

    @Schema(description = "最大开班人数")
    private Integer maxStudents;

    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
