package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.context.AuthenticatedUser;

public interface PermissionService {

    boolean hasPermission(AuthenticatedUser user, String permission);
}
