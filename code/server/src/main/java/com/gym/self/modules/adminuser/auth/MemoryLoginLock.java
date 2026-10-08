package com.gym.self.modules.adminuser.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"test", "local"})
public class MemoryLoginLock implements LoginLock {

    private static final int LIMIT = 5;
    private static final long WINDOW_SECONDS = 600;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean locked(String username) {
        Bucket bucket = buckets.get(username);
        if (bucket == null || bucket.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(Instant.now())) {
            return false;
        }
        return bucket.count >= LIMIT;
    }

    @Override
    public void recordFailure(String username) {
        buckets.compute(username, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new Bucket(1, now);
            }
            return new Bucket(existing.count + 1, existing.windowStart);
        });
    }

    @Override
    public void clear(String username) {
        buckets.remove(username);
    }

    private record Bucket(int count, Instant windowStart) {
    }
}
