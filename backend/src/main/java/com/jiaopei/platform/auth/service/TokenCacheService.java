package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.context.AuthenticatedUser;

import java.util.concurrent.TimeUnit;

public interface TokenCacheService {

    void storeToken(String token, AuthenticatedUser user, long duration, TimeUnit unit);

    AuthenticatedUser getUser(String token);

    void removeToken(String token);

    void invalidateUserTokens(Long userId);
}
