package com.cornerstone.module.organization.listener;

import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.module.organization.mapper.UserCampusPrefMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号注销时的组织域数据清理监听器。
 *
 * <p>校区偏好是「某个账号在本机构默认看哪个校区」，账号没了这行就没有归属，
 * 且 user_id 带外键指向 sys_user_account，不清则删账号直接被 1451 拒掉。</p>
 *
 * <p>与注销流程同事务，任一清理失败整体回滚。</p>
 */
@Component
public class OrganizationDeregistrationListener {

    private final UserCampusPrefMapper userCampusPrefMapper;

    public OrganizationDeregistrationListener(UserCampusPrefMapper userCampusPrefMapper) {
        this.userCampusPrefMapper = userCampusPrefMapper;
    }

    @EventListener
    public void onUserAccountDeregistered(UserAccountDeregisteredEvent event) {
        userCampusPrefMapper.physicalDeleteByUserId(event.userId());
    }
}
