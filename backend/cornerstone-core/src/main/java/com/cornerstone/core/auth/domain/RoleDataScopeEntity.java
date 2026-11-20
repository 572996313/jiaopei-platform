package com.cornerstone.core.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色数据范围授权。一条记录 = 某角色在某机构的某维度里包含某个节点。
 *
 * <p>未配置任何记录表示该维度不限制；{@code scopeValue = 0} 表示该维度全部；
 * 其余值为白名单。具体节点是否合法由各业务模块的 {@code RoleDataScopeDimension} 校验，
 * 底座只负责存储与登录快照解析。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_data_scope")
public class RoleDataScopeEntity extends BaseEntity {

    private Long roleId;
    private Long institutionId;
    private String scopeType;
    private Long scopeValue;
}
