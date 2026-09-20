package com.cornerstone.core.auth.aspect;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.exception.PermissionDeniedException;
import com.cornerstone.core.auth.service.PermissionService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionGuardAspect {

    private final PermissionService permissionService;

    public PermissionGuardAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Before("@annotation(requiresPermission)")
    public void checkMethodPermission(RequiresPermission requiresPermission) {
        verifyPermission(requiresPermission);
    }

    @Before("@within(requiresPermission)")
    public void checkClassPermission(RequiresPermission requiresPermission) {
        verifyPermission(requiresPermission);
    }

    private void verifyPermission(RequiresPermission requiresPermission) {
        AuthenticatedUser user = AuthContext.requireUser();
        if (!permissionService.hasPermission(user, requiresPermission.value())) {
            throw new PermissionDeniedException(requiresPermission.value());
        }
    }
}
