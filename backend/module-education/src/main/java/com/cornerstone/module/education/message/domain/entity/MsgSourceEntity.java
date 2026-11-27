package com.cornerstone.module.education.message.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事件数据源视图目录。
 *
 * <p>开发写迁移登记只读视图（列契约固定），运维只在视图上配事件类型；
 * 执行器只从本表取白名单视图名，不接受任何前端传入的 SQL/表名。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_source")
@Schema(description = "事件数据源视图目录")
public class MsgSourceEntity extends BaseEntity {

    @Schema(description = "数据源代码(代码判定,不入字典)")
    private String sourceCode;

    @Schema(description = "数据源名称")
    private String displayName;

    @Schema(description = "只读视图名(白名单)")
    private String viewName;

    @Schema(description = "说明(返回哪些行、列契约)")
    private String description;

    @Schema(description = "列契约(最近一次校验发现的列,逗号分隔)")
    private String columnContract;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "最近一次列校验时间")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDateTime lastValidatedAt;
}
