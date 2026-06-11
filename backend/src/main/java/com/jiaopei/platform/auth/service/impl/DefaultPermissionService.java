package com.jiaopei.platform.auth.service.impl;

import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.service.PermissionService;
import org.springframework.stereotype.Service;

@Service
public class DefaultPermissionService implements PermissionService {

    @Override
    public boolean hasPermission(AuthenticatedUser user, String permission) {
        return user != null
            && user.permissions() != null
            && user.permissions().contains(permission);
    }
}
