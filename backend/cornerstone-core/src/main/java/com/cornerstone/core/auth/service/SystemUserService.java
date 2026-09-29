package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.dto.ResetPasswordResponse;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.dto.UserPageQuery;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.dto.UserUpdateRequest;
import com.cornerstone.core.auth.dto.UserView;
import com.cornerstone.core.common.api.PageResult;
import java.util.List;

public interface SystemUserService {

    List<UserView> listUsers();

    PageResult<UserView> pageUsers(UserPageQuery query);

    UserView createUser(UserCreateRequest request);

    UserView updateUser(Long id, UserUpdateRequest request);

    ResetPasswordResponse resetPassword(Long id);

    UserView updateRoles(Long id, UserRoleUpdateRequest request);

    /**
     * 注销账号：物理删除账号行及其角色关联、注册凭证，业务域档案的归属由
     * {@link com.cornerstone.core.auth.event.UserAccountDeregisteredEvent} 的监听器清理。
     *
     * <p>必须是已停用的账号才能注销，且不能注销自己或最后一个可用的平台管理员。</p>
     */
    void deregisterUser(Long id);
}
