package com.gym.self.modules.adminuser.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test & !local")
public class RedisLoginLock implements LoginLock {

    private static final int LIMIT = 5;
    private final StringRedisTemplate redis;

    public RedisLoginLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean locked(String username) {
        String value = redis.opsForValue().get(key(username));
        return value != null && Integer.parseInt(value) >= LIMIT;
    }

    @Override
    public void recordFailure(String username) {
        String key = key(username);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(10));
        }
    }

    @Override
    public void clear(String username) {
        redis.delete(key(username));
    }

    private String key(String username) {
        return "admin:login:fail:" + username;
    }
}
