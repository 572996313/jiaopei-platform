package com.cornerstone.core.auth.service.impl;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.service.TokenCacheService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class TokenCacheServiceImpl implements TokenCacheService {

    private static final String TOKEN_KEY_PREFIX = "jiaopei:token:";
    private static final String USER_TOKENS_KEY_PREFIX = "jiaopei:user:tokens:";

    private final RedisTemplate<String, Object> redisTemplate;

    public TokenCacheServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void storeToken(String token, AuthenticatedUser user, long duration, TimeUnit unit) {
        String tokenKey = TOKEN_KEY_PREFIX + token;
        String userTokensKey = USER_TOKENS_KEY_PREFIX + user.userId();

        redisTemplate.opsForValue().set(tokenKey, user, duration, unit);
        redisTemplate.opsForSet().add(userTokensKey, token);
        redisTemplate.expire(userTokensKey, duration, unit);
    }

    @Override
    public AuthenticatedUser getUser(String token) {
        String tokenKey = TOKEN_KEY_PREFIX + token;
        Object value = redisTemplate.opsForValue().get(tokenKey);
        return value instanceof AuthenticatedUser ? (AuthenticatedUser) value : null;
    }

    @Override
    public void removeToken(String token) {
        String tokenKey = TOKEN_KEY_PREFIX + token;

        AuthenticatedUser user = getUser(token);
        if (user != null) {
            String userTokensKey = USER_TOKENS_KEY_PREFIX + user.userId();
            redisTemplate.opsForSet().remove(userTokensKey, token);
        }

        redisTemplate.delete(tokenKey);
    }

    @Override
    public void invalidateUserTokens(Long userId) {
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

        Set<Object> tokens = redisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null && !tokens.isEmpty()) {
            for (Object token : tokens) {
                String tokenKey = TOKEN_KEY_PREFIX + token;
                redisTemplate.delete(tokenKey);
            }
        }

        redisTemplate.delete(userTokensKey);
    }
}
