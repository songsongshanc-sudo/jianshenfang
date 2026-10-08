package com.gym.self.modules.adminuser.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"test", "local"})
public class MemoryTokenStore implements TokenStore {

    private final Map<String, Record> tokens = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> byAdmin = new ConcurrentHashMap<>();

    @Override
    public void save(long adminId, String jti, String kind, long ttlSeconds) {
        tokens.put(jti, new Record(adminId, Instant.now().plusSeconds(ttlSeconds)));
        byAdmin.computeIfAbsent(adminId, key -> ConcurrentHashMap.newKeySet()).add(jti);
    }

    @Override
    public boolean valid(String jti) {
        Record record = tokens.get(jti);
        if (record == null) {
            return false;
        }
        if (record.expireAt.isBefore(Instant.now())) {
            tokens.remove(jti);
            return false;
        }
        return true;
    }

    @Override
    public void revokeAdmin(long adminId) {
        Set<String> jtis = byAdmin.remove(adminId);
        if (jtis != null) {
            jtis.forEach(tokens::remove);
        }
    }

    private record Record(long adminId, Instant expireAt) {
    }
}
