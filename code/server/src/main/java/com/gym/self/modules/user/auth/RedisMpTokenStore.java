package com.gym.self.modules.user.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test & !local")
public class RedisMpTokenStore implements MpTokenStore {

    private final StringRedisTemplate redis;

    public RedisMpTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(long userId, String jti, long ttlSeconds) {
        redis.opsForValue().set(key(jti), String.valueOf(userId), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean valid(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(jti)));
    }

    @Override
    public void revoke(String jti) {
        redis.delete(key(jti));
    }

    private static String key(String jti) {
        return "mp:token:" + jti;
    }
}
