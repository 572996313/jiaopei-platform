package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.dto.ChangePasswordRequest;
import com.cornerstone.core.auth.dto.CurrentUserResponse;
import com.cornerstone.core.auth.dto.LoginRequest;
import com.cornerstone.core.auth.dto.LoginResponse;

public interface AuthApplicationService {

    String FORCE_CHANGE_ENABLED_KEY = "auth.password.force_change_enabled";

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    AuthenticatedUser authenticateToken(String accessToken);

    CurrentUserResponse currentUser();

    void changePassword(ChangePasswordRequest request);

    void invalidateUserSessions(Long userId);
}
