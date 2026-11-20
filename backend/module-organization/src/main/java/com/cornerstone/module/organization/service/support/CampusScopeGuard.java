package com.cornerstone.module.organization.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 校区数据范围强制收口。
 *
 * <p>登录快照里的 {@code dataScopeKey/dataScopeIds} 是唯一事实源；前端传来的 campusId 只是
 * 额外筛选条件，为 null 绝不放宽服务端范围。受限且白名单为空时按拒绝处理（{@code 1 = 0} / 403），
 * 不能在删校区、清空授权后反而变成"看全部"。</p>
 *
 * <p>做成静态工具类而不是注入的 Bean：几十个业务服务的 Mockito 夹具都是手工 new 的，
 * 加构造参数会无谓地扩散改动；读 SecurityContext 也不需要容器参与。</p>
 */
public final class CampusScopeGuard {

    public static final String DIMENSION_KEY = "CAMPUS";
    private static final String PORTAL_ADMIN = "admin";

    private CampusScopeGuard() {
    }

    /** 是否受校区范围限制；无登录态（纯单测）视为不限制。 */
    public static boolean restricted() {
        return AuthContext.currentUser()
            .map(user -> PORTAL_ADMIN.equals(user.portal())
                && DIMENSION_KEY.equals(user.dataScopeKey())
                && user.dataScopeIds() != null)
            .orElse(false);
    }

    /** null=不限制；非 null=白名单（空集表示没有任何可见校区）。 */
    public static Set<Long> campusIds() {
        if (!restricted()) {
            return null;
        }
        return AuthContext.requireUser().dataScopeIds();
    }

    public static boolean canAccess(Long campusId) {
        Set<Long> ids = campusIds();
        return ids == null || (campusId != null && ids.contains(campusId));
    }

    /** 非空归属校区列：受限时追加 {@code campus_id IN (...)}。 */
    public static <T> void apply(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> column) {
        Set<Long> ids = campusIds();
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(column, ids);
    }

    /** 可空列：NULL 表示机构通用/未分配，受限用户仍可见，但不能写新的 NULL。 */
    public static <T> void applyIncludingNull(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> column) {
        Set<Long> ids = campusIds();
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(w -> w.isNull(column).or().in(column, ids));
    }

    /** 哨兵列（如校区日历 0=全校区）：哨兵行对所有受限用户可见。 */
    public static <T> void applyWithAllSentinel(LambdaQueryWrapper<T> wrapper,
                                                SFunction<T, Long> column,
                                                long allValue) {
        Set<Long> ids = campusIds();
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(w -> w.eq(column, allValue).or().in(column, ids));
    }

    /**
     * 无校区列、只能经班级推导的表（报名/班级学员/考勤）：追加 class_id 子查询。
     * 子查询里的 id 来自服务端会话快照，均为数字，不存在拼接注入。
     */
    public static <T> void applyClassIds(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> classIdColumn) {
        Set<Long> ids = campusIds();
        if (ids == null) {
            return;
        }
        Long institutionId = AuthContext.requireUser().orgId();
        if (ids.isEmpty() || institutionId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.inSql(classIdColumn, accessibleClassIdSql(institutionId, ids));
    }

    public static String accessibleClassIdSql(Long institutionId, Set<Long> campusIds) {
        String idList = campusIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "SELECT id FROM jp_class WHERE deleted = 0 AND institution_id = " + institutionId
            + " AND campus_id IN (" + idList + ")";
    }

    /** 校验目标校区在范围内；提示用业务对象中文名，不暴露雪花 ID。 */
    public static void assertCampus(Long campusId, String subject) {
        Set<Long> ids = campusIds();
        if (ids != null && (campusId == null || !ids.contains(campusId))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他校区的" + subject);
        }
    }

    /** 可空校区列的写校验：受限用户不能写 NULL（机构通用），目标校区必须在范围内。 */
    public static void assertNullableCampus(Long campusId, String subject) {
        Set<Long> ids = campusIds();
        if (ids == null) {
            return;
        }
        if (campusId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "当前角色无权维护机构通用的" + subject);
        }
        if (!ids.contains(campusId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他校区的" + subject);
        }
    }

    /** 新建校区、全校区公告、全校区日历等只有不受限角色能做。 */
    public static void assertUnrestricted(String action) {
        if (restricted()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前角色无权" + action);
        }
    }

    /** 从快照直接取维度，供无法用 wrapper 的 EXISTS 场景复用。 */
    public static boolean matches(AuthenticatedUser user) {
        return user != null
            && PORTAL_ADMIN.equals(user.portal())
            && DIMENSION_KEY.equals(user.dataScopeKey())
            && user.dataScopeIds() != null;
    }
}
