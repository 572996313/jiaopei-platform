package com.cornerstone.module.organization.service.support;

import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.util.Objects;

/**
 * 机构归属收口：登录态机构是唯一事实源，客户端传来的 institutionId 只当筛选条件。
 * 不传机构就按登录机构查，传别的机构直接 403；平台管理员保留跨机构筛选能力。
 *
 * <p>校区数据范围必须建在这层之上，否则「少传一个参数等于读全库」，
 * 后面的校区收窄就只是装饰品。</p>
 */
public final class InstitutionScopeGuard {

    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private InstitutionScopeGuard() {
    }

    /**
     * 查询范围：null/相同 = 登录机构；不同 = 仅平台管理员可用。
     *
     * <p>无登录态（纯单测直接 new Service）返回入参，由调用方决定是否加机构条件；
     * 生产请求一定会先经过认证过滤器，正常路径不会走到这个分支。</p>
     */
    public static Long resolveForQuery(Long requestedInstitutionId, String subject) {
        AuthenticatedUser user = AuthContext.currentUser().orElse(null);
        if (user == null) {
            return requestedInstitutionId;
        }
        Long current = user.orgId();
        if (requestedInstitutionId == null || Objects.equals(requestedInstitutionId, current)) {
            if (current == null) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                    "当前登录账号未归属任何机构，无法访问" + subject);
            }
            return current;
        }
        if (isPlatformAdmin(user)) {
            return requestedInstitutionId;
        }
        throw forbidden(subject);
    }

    /** 数据归属校验：要读/要改的那行所属机构必须等于登录机构。 */
    public static void assertOwn(Long institutionId, String subject) {
        AuthenticatedUser user = AuthContext.currentUser().orElse(null);
        if (user == null || Objects.equals(institutionId, user.orgId()) || isPlatformAdmin(user)) {
            return;
        }
        throw forbidden(subject);
    }

    public static Long currentInstitution(String subject) {
        AuthenticatedUser user = AuthContext.currentUser().orElse(null);
        if (user == null) {
            return null;
        }
        Long current = user.orgId();
        if (current == null) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "当前登录账号未归属任何机构，无法访问" + subject);
        }
        return current;
    }

    private static boolean isPlatformAdmin(AuthenticatedUser user) {
        return user.roles() != null && user.roles().contains(PLATFORM_ADMIN_ROLE);
    }

    private static BusinessException forbidden(String subject) {
        return new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的" + subject);
    }
}
