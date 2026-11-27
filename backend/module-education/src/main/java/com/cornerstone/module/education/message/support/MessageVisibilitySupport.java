package com.cornerstone.module.education.message.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import com.cornerstone.module.education.domain.entity.UserStudentRelEntity;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 事件可见性。
 *
 * <p>口径：内部员工先能看到本机构全部事件（订阅只管注意力）；校区范围开关
 * {@code message.event.campus-scope.enabled} 默认 false，权限口径确定后改 true 即按
 * CampusScopeGuard 收窄，不用改代码。家长/学生端只能看到 visibility=parent/student
 * 且学员锚点是自己绑定学员的事件。</p>
 */
@Component
public class MessageVisibilitySupport {

    public static final String CAMPUS_SCOPE_KEY = "message.event.campus-scope.enabled";
    private static final String PORTAL_ADMIN = "admin";

    private final SystemConfigService systemConfigService;
    private final MsgEventTypeMapper typeMapper;
    private final UserStudentRelMapper userStudentRelMapper;

    public MessageVisibilitySupport(SystemConfigService systemConfigService,
                                    MsgEventTypeMapper typeMapper,
                                    UserStudentRelMapper userStudentRelMapper) {
        this.systemConfigService = systemConfigService;
        this.typeMapper = typeMapper;
        this.userStudentRelMapper = userStudentRelMapper;
    }

    public boolean campusScopeEnabled() {
        return Boolean.TRUE.equals(systemConfigService.getBooleanConfig(CAMPUS_SCOPE_KEY, false));
    }

    /** 事件流的校区收窄；campus_id 为 NULL 表示机构通用，受限用户仍可见。 */
    public void applyCampusScope(LambdaQueryWrapper<MsgEventEntity> wrapper) {
        if (campusScopeEnabled()) {
            CampusScopeGuard.applyIncludingNull(wrapper, MsgEventEntity::getCampusId);
        }
    }

    /** 非后台端（家长/学生）按可见端 + 绑定学员收窄；后台端不加限制。 */
    public void applyPortalScope(LambdaQueryWrapper<MsgEventEntity> wrapper, AuthenticatedUser user) {
        if (user == null || PORTAL_ADMIN.equals(user.portal())) {
            return;
        }
        List<String> typeCodes = typeMapper.selectList(new LambdaQueryWrapper<MsgEventTypeEntity>()
                .eq(MsgEventTypeEntity::getVisibility, user.portal())
                .eq(MsgEventTypeEntity::getEnabled, true))
            .stream()
            .map(MsgEventTypeEntity::getTypeCode)
            .toList();
        Set<Long> studentIds = boundStudentIds(user.userId());
        if (typeCodes.isEmpty() || studentIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(MsgEventEntity::getTypeCode, typeCodes)
            .in(MsgEventEntity::getAnchorStudentId, studentIds);
    }

    public void assertVisible(MsgEventEntity event) {
        AuthenticatedUser user = AuthContext.requireUser();
        if (event == null || !Objects.equals(event.getInstitutionId(), user.orgId())) {
            throw notFound();
        }
        if (campusScopeEnabled()) {
            Set<Long> campusIds = CampusScopeGuard.campusIds();
            if (campusIds != null) {
                if (campusIds.isEmpty()) {
                    throw notFound();
                }
                if (event.getCampusId() != null && !campusIds.contains(event.getCampusId())) {
                    throw notFound();
                }
            }
        }
        if (!PORTAL_ADMIN.equals(user.portal())) {
            MsgEventTypeEntity type = typeMapper.selectOne(new LambdaQueryWrapper<MsgEventTypeEntity>()
                .eq(MsgEventTypeEntity::getTypeCode, event.getTypeCode())
                .last("LIMIT 1"));
            if (type == null || !user.portal().equals(type.getVisibility())) {
                throw notFound();
            }
            if (event.getAnchorStudentId() == null
                || !boundStudentIds(user.userId()).contains(event.getAnchorStudentId())) {
                throw notFound();
            }
        }
    }

    public Set<Long> boundStudentIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return userStudentRelMapper.selectList(new LambdaQueryWrapper<UserStudentRelEntity>()
                .eq(UserStudentRelEntity::getUserId, userId))
            .stream()
            .map(UserStudentRelEntity::getStudentId)
            .collect(Collectors.toSet());
    }

    private BusinessException notFound() {
        // 不区分"不存在"和"无权看"，避免用返回码探测别人的事件
        return new BusinessException(ResultCode.NOT_FOUND, "事件不存在或已不可见");
    }
}
