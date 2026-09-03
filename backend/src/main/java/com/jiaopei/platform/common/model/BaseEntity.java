package com.jiaopei.platform.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public abstract class BaseEntity {

    @Schema(description = "主键ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "逻辑删除标识(0=未删除,1=已删除)")
    @TableField("deleted")
    @TableLogic
    private Boolean deleted;
}
