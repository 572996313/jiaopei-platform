package com.cornerstone.module.education.service.support;

import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.util.Objects;

/**
 * 排课域（课次/周期计划/排课规则）的机构归属收口：登录态机构是唯一事实源，
 * 客户端传来的 institutionId 只当筛选条件。
 *
 * <p>口径与 {@code ScheduleCompletenessServiceImpl#requireInstitution} 一致：不传机构就按登录机构查，
 * 传了别的机构直接 FORBIDDEN。放开这条就是"少传一个参数等于读全库"——
 * 排课的三个查询接口都因此被实测过能跨机构读改数据。
 */
public final class ScheduleInstitutionGuard {

    private ScheduleInstitutionGuard() {
    }

    /**
     * 解析查询范围：入参为 null 或等于登录机构时通过，返回登录机构 ID，可直接进 wrapper。
     *
     * @param subject 数据对象中文名，用于拼出「无权访问其他机构的排课课次」这类提示
     */
    public static Long resolveForQuery(Long requestedInstitutionId, String subject) {
        Long institutionId = currentInstitution();
        if (requestedInstitutionId != null && !Objects.equals(requestedInstitutionId, institutionId)) {
            throw forbidden(subject);
        }
        return institutionId;
    }

    /** 校验数据归属：入参是"要读/要改的那行所属机构"或"请求声明的机构"，必须等于登录机构。 */
    public static void assertOwn(Long institutionId, String subject) {
        if (!Objects.equals(institutionId, currentInstitution())) {
            throw forbidden(subject);
        }
    }

    private static Long currentInstitution() {
        Long institutionId = AuthContext.requireUser().orgId();
        if (institutionId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前登录账号未归属任何机构，无法访问排课数据");
        }
        return institutionId;
    }

    private static BusinessException forbidden(String subject) {
        return new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的" + subject);
    }
}
