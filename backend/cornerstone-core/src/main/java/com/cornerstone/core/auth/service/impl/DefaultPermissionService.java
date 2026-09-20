package com.cornerstone.core.auth.service.impl;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.service.PermissionService;
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
