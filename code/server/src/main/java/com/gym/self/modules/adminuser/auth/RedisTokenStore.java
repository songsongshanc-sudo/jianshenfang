package com.gym.self.modules.adminuser.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
@Profile("!test & !local")
public class RedisTokenStore implements TokenStore {

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(long adminId, String jti, String kind, long ttlSeconds) {
        redis.opsForValue().set(key(kind, jti), String.valueOf(adminId), Duration.ofSeconds(ttlSeconds));
        redis.opsForSet().add(adminKey(adminId), jti);
    }

    @Override
    public boolean valid(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key("access", jti)))
                || Boolean.TRUE.equals(redis.hasKey(key("refresh", jti)));
    }

    @Override
    public void revokeAdmin(long adminId) {
        Set<String> jtis = redis.opsForSet().members(adminKey(adminId));
        if (jtis != null) {
            jtis.forEach(jti -> {
                redis.delete(key("access", jti));
                redis.delete(key("refresh", jti));
            });
        }
        redis.delete(adminKey(adminId));
    }

    private String key(String kind, String jti) {
        return "admin:" + kind + ":" + jti;
    }

    private String adminKey(long adminId) {
        return "admin:tokens:" + adminId;
    }
}
