package com.cornerstone.module.education.message.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 个人事件订阅规则。
 *
 * <p>订阅只负责从全量事件流里收窄注意力，不是权限边界：同维度多规则取 OR，
 * 跨维度取 AND，没有任何规则等于全部可见事件。漏勾选由线下沟通与补录兜底。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_subscription")
@Schema(description = "个人事件订阅规则")
public class MsgSubscriptionEntity extends BaseEntity {

    @Schema(description = "账号ID")
    private Long userId;

    @Schema(description = "规则维度(type_code/category/campus/class/student/teacher/level/keyword)")
    private String ruleType;

    @Schema(description = "规则值(维度对应的ID或关键字)")
    private String ruleValue;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
