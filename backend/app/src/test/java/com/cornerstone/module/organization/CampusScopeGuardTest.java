package com.cornerstone.module.organization;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampusScopeGuardTest {

    /** 单独跑本测试时没有 Spring 上下文，lambda 列名缓存要手动初始化，否则 getSqlSegment() 直接抛错 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CampusEntity.class);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restrictedRoleNarrowsQueriesToWhitelist() {
        authenticate("admin", "CAMPUS", Set.of(11L, 12L));

        assertTrue(CampusScopeGuard.restricted());
        assertEquals(Set.of(11L, 12L), CampusScopeGuard.campusIds());
        assertTrue(CampusScopeGuard.canAccess(11L));
        assertFalse(CampusScopeGuard.canAccess(99L));

        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<>();
        CampusScopeGuard.apply(wrapper, CampusEntity::getId);

        assertTrue(wrapper.getSqlSegment().contains("IN"), wrapper.getSqlSegment());
    }

    @Test
    void emptyWhitelistFailsClosedInsteadOfOpeningUp() {
        // 删光校区授权后必须一条都看不到，不能退化成"没配置 = 全部"
        authenticate("admin", "CAMPUS", Set.of());

        assertTrue(CampusScopeGuard.restricted());
        assertFalse(CampusScopeGuard.canAccess(11L));

        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<>();
        CampusScopeGuard.apply(wrapper, CampusEntity::getId);
        assertTrue(wrapper.getSqlSegment().replace(" ", "").contains("1=0"), wrapper.getSqlSegment());

        assertThrows(BusinessException.class, () -> CampusScopeGuard.assertCampus(11L, "校区"));
        assertThrows(BusinessException.class, () -> CampusScopeGuard.assertUnrestricted("新建校区"));
    }

    @Test
    void nonAdminPortalStaysUnrestricted() {
        // 家长/学生小程序走同一套服务，不能被后台角色的范围误伤
        authenticate("parent", "CAMPUS", Set.of(11L));

        assertFalse(CampusScopeGuard.restricted());
        assertNull(CampusScopeGuard.campusIds());
        assertTrue(CampusScopeGuard.canAccess(999L));
    }

    @Test
    void nullableCampusColumnTreatsNullAsInstitutionWide() {
        authenticate("admin", "CAMPUS", Set.of(11L));

        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<>();
        CampusScopeGuard.applyIncludingNull(wrapper, CampusEntity::getInstitutionId);
        assertTrue(wrapper.getSqlSegment().contains("IS NULL"), wrapper.getSqlSegment());

        // 读可以看到机构通用行，写则必须拦住
        assertThrows(BusinessException.class, () -> CampusScopeGuard.assertNullableCampus(null, "教师档案"));
        assertThrows(BusinessException.class, () -> CampusScopeGuard.assertNullableCampus(99L, "教师档案"));
        CampusScopeGuard.assertNullableCampus(11L, "教师档案");
    }

    @Test
    void noAuthenticationMeansUnrestrictedForUnitTests() {
        assertFalse(CampusScopeGuard.restricted());
        assertNull(CampusScopeGuard.campusIds());
        CampusScopeGuard.assertCampus(123L, "校区");
    }

    private void authenticate(String portal, String dataScopeKey, Set<Long> dataScopeIds) {
        AuthenticatedUser user = new AuthenticatedUser(1L, 9L, "operator", Set.of(), Set.of(),
            Boolean.FALSE, portal, dataScopeKey, dataScopeIds);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
