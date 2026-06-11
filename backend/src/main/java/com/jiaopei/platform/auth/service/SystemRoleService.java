package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.dto.RoleCreateRequest;
import com.jiaopei.platform.auth.dto.RoleUpdateRequest;
import com.jiaopei.platform.auth.dto.RoleView;
import java.util.List;

public interface SystemRoleService {

    List<RoleView> listRoles();

    RoleView createRole(RoleCreateRequest request);

    RoleView updateRole(Long id, RoleUpdateRequest request);
}
