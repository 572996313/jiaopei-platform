package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.dto.RoleCreateRequest;
import com.cornerstone.core.auth.dto.RolePageQuery;
import com.cornerstone.core.auth.dto.RoleUpdateRequest;
import com.cornerstone.core.auth.dto.RoleView;
import com.cornerstone.core.common.api.PageResult;
import java.util.List;

public interface SystemRoleService {

    List<RoleView> listRoles();

    PageResult<RoleView> pageRoles(RolePageQuery query);

    RoleView createRole(RoleCreateRequest request);

    RoleView updateRole(Long id, RoleUpdateRequest request);
}
