package com.jiaopei.platform.auth.aspect;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.auth.context.AuthContext;
import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.exception.PermissionDeniedException;
import com.jiaopei.platform.auth.service.PermissionService;
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
