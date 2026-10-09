package com.gym.self.modules.gate;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"local", "test"})
public class MemoryGateState implements GateState {

    private final Map<String, Instant> nonces = new ConcurrentHashMap<>();
    private final Map<String, Instant> debounce = new ConcurrentHashMap<>();

    @Override
    public boolean firstNonce(String deviceSn, String nonce, Duration ttl) {
        Instant now = Instant.now();
        nonces.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        return nonces.putIfAbsent(deviceSn + ":" + nonce, now.plus(ttl)) == null;
    }

    @Override
    public boolean withinDebounce(long userId, String deviceSn, Duration window) {
        Instant now = Instant.now();
        String key = userId + ":" + deviceSn;
        Instant until = debounce.get(key);
        if (until != null && until.isAfter(now)) {
            return true;
        }
        debounce.put(key, now.plus(window));
        return false;
    }
}
