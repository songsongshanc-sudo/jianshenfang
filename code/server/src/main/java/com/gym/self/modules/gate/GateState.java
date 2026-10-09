package com.gym.self.modules.gate;

import java.time.Duration;

public interface GateState {

    boolean firstNonce(String deviceSn, String nonce, Duration ttl);

    boolean withinDebounce(long userId, String deviceSn, Duration window);
}
