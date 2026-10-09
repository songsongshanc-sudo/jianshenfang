package com.gym.self.modules.gate;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!local & !test")
public class RedisGateState implements GateState {

    private final StringRedisTemplate redis;

    public RedisGateState(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean firstNonce(String deviceSn, String nonce, Duration ttl) {
        Boolean created = redis.opsForValue().setIfAbsent("gate:nonce:" + deviceSn + ":" + nonce, "1", ttl);
        return Boolean.TRUE.equals(created);
    }

    @Override
    public boolean withinDebounce(long userId, String deviceSn, Duration window) {
        Boolean created = redis.opsForValue().setIfAbsent("entry:debounce:" + userId + ":" + deviceSn, "1", window);
        return !Boolean.TRUE.equals(created);
    }
}
