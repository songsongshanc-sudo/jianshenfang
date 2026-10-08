package com.gym.self.common.id;

import org.springframework.stereotype.Component;

@Component
public class Snowflake {

    private static final long EPOCH = 1_700_000_000_000L;
    private final long workerId = 1L;
    private long sequence;
    private long lastTimestamp = -1L;

    public synchronized long next() {
        long now = System.currentTimeMillis();
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & 4095;
            if (sequence == 0) {
                while (now <= lastTimestamp) {
                    now = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << 22) | (workerId << 12) | sequence;
    }
}
