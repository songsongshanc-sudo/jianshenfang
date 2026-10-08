package com.gym.self.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class TimeProvider {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private volatile Clock clock = Clock.system(ZONE);

    public LocalTime localTime() {
        return LocalTime.now(clock);
    }

    public void use(Clock clock) {
        this.clock = clock;
    }

    public void reset() {
        this.clock = Clock.system(ZONE);
    }
}
