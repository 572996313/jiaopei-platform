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
}
