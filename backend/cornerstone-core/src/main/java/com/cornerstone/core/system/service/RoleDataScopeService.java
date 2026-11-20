package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.RoleDataScopeUpdate;
import com.cornerstone.core.system.dto.RoleDataScopeView;
import java.util.List;

/**
 * 角色数据范围授权：底座只负责读写 {@code sys_role_data_scope}，
 * 维度选项与合法性校验由业务模块注册的 {@code RoleDataScopeDimension} 提供。
 */
public interface RoleDataScopeService {

    /** 读取角色在当前机构下的各维度范围；未装配任何维度时返回空列表。 */
    List<RoleDataScopeView> list(Long roleId, Long institutionId);

    /** 整组替换指定维度的范围；{@code updates == null} 表示不改动。 */
    void replace(Long roleId, Long institutionId, List<RoleDataScopeUpdate> updates);
}
