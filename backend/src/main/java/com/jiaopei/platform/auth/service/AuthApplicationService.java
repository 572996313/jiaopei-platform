package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.context.AuthenticatedUser;
import com.jiaopei.platform.auth.dto.ChangePasswordRequest;
import com.jiaopei.platform.auth.dto.CurrentUserResponse;
import com.jiaopei.platform.auth.dto.LoginRequest;
import com.jiaopei.platform.auth.dto.LoginResponse;

public interface AuthApplicationService {

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    AuthenticatedUser authenticateToken(String accessToken);

    CurrentUserResponse currentUser();

    void changePassword(ChangePasswordRequest request);

    void invalidateUserSessions(Long userId);
}
