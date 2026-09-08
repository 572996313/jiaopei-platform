package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.dto.ResetPasswordResponse;
import com.jiaopei.platform.auth.dto.UserCreateRequest;
import com.jiaopei.platform.auth.dto.UserPageQuery;
import com.jiaopei.platform.auth.dto.UserRoleUpdateRequest;
import com.jiaopei.platform.auth.dto.UserUpdateRequest;
import com.jiaopei.platform.auth.dto.UserView;
import com.jiaopei.platform.common.api.PageResult;
import java.util.List;

public interface SystemUserService {

    List<UserView> listUsers();

    PageResult<UserView> pageUsers(UserPageQuery query);

    UserView createUser(UserCreateRequest request);

    UserView updateUser(Long id, UserUpdateRequest request);

    ResetPasswordResponse resetPassword(Long id);

    UserView updateRoles(Long id, UserRoleUpdateRequest request);
}
