package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.context.AuthenticatedUser;

import java.util.concurrent.TimeUnit;

public interface TokenCacheService {

    void storeToken(String token, AuthenticatedUser user, long duration, TimeUnit unit);

    AuthenticatedUser getUser(String token);

    void removeToken(String token);

    void invalidateUserTokens(Long userId);
}
