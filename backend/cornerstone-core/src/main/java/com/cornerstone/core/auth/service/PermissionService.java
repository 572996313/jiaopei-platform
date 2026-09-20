package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.context.AuthenticatedUser;

public interface PermissionService {

    boolean hasPermission(AuthenticatedUser user, String permission);
}
