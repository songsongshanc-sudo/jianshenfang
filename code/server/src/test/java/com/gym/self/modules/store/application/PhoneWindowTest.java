package com.gym.self.modules.store.application;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneWindowTest {

    private final LocalTime nightStart = LocalTime.of(21, 30);
    private final LocalTime nightEnd = LocalTime.of(9, 0);

    @Test
    void nightShiftStartsAt2130AndCrossesMidnight() {
        assertFalse(PhoneWindow.contains(LocalTime.of(21, 29), nightStart, nightEnd));
        assertTrue(PhoneWindow.contains(LocalTime.of(21, 30), nightStart, nightEnd));
        assertTrue(PhoneWindow.contains(LocalTime.of(23, 59), nightStart, nightEnd));
        assertTrue(PhoneWindow.contains(LocalTime.of(0, 10), nightStart, nightEnd));
        assertTrue(PhoneWindow.contains(LocalTime.of(8, 59), nightStart, nightEnd));
        assertFalse(PhoneWindow.contains(LocalTime.of(9, 0), nightStart, nightEnd));
    }

    @Test
    void dayShiftEndsWhenNightStarts() {
        LocalTime dayStart = LocalTime.of(9, 0);
        LocalTime dayEnd = LocalTime.of(21, 30);
        assertTrue(PhoneWindow.contains(LocalTime.of(9, 0), dayStart, dayEnd));
        assertTrue(PhoneWindow.contains(LocalTime.of(21, 29), dayStart, dayEnd));
        assertFalse(PhoneWindow.contains(LocalTime.of(21, 30), dayStart, dayEnd));
        assertFalse(PhoneWindow.contains(LocalTime.of(0, 10), dayStart, dayEnd));
    }
}
