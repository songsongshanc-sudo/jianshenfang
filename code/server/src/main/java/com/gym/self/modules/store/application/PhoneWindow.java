package com.gym.self.modules.store.application;

import java.time.LocalTime;

public final class PhoneWindow {

    private PhoneWindow() {
    }

    /**
     * 结束时间早于开始时间时，时段跨过 0 点。结束时刻本身不算在时段内。
     * 开始和结束都为空表示全天。
     */
    public static boolean contains(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null && end == null) {
            return true;
        }
        if (now == null || start == null || end == null || start.equals(end)) {
            return false;
        }
        if (end.isAfter(start)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
