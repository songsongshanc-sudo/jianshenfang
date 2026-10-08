package com.gym.self.modules.user.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"test", "local"})
public class MemoryMpTokenStore implements MpTokenStore {

    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

    @Override
    public void save(long userId, String jti, long ttlSeconds) {
        tokens.put(jti, Instant.now().plusSeconds(ttlSeconds));
    }

    @Override
    public boolean valid(String jti) {
        Instant expireAt = tokens.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (expireAt.isBefore(Instant.now())) {
            tokens.remove(jti);
            return false;
        }
        return true;
    }

    @Override
    public void revoke(String jti) {
        tokens.remove(jti);
    }
}
