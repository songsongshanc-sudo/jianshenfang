package com.gym.self.modules.card;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipProgressTest {

    @Test
    void gapResetsConsecutiveDaysAndMergedRangesCountOnce() {
        LocalDate today = LocalDate.of(2026, 1, 20);
        MembershipProgress.Result result = MembershipProgress.of(List.of(
                span(1L, "2026-01-01T00:00", "2026-01-11T00:00"),
                span(1L, "2026-01-05T08:00", "2026-01-08T08:00"),
                span(1L, "2026-01-12T00:00", "2026-01-21T00:00")
        ), today, 1L);
        assertEquals(19, result.cumulativeDays());
        assertEquals(9, result.consecutiveDays());
        assertTrue(result.memberToday());
    }

    @Test
    void brokenTodayZerosConsecutiveDays() {
        LocalDate today = LocalDate.of(2026, 1, 21);
        MembershipProgress.Result result = MembershipProgress.of(List.of(
                span(1L, "2026-01-01T00:00", "2026-01-21T00:00")
        ), today, 1L);
        assertEquals(20, result.cumulativeDays());
        assertEquals(0, result.consecutiveDays());
        assertFalse(result.memberToday());
    }

    @Test
    void expiringLaterTodayStillCounts() {
        LocalDate today = LocalDate.of(2026, 10, 8);
        MembershipProgress.Result result = MembershipProgress.of(List.of(
                span(2L, "2026-10-07T10:00", "2026-10-08T10:00")
        ), today, 2L);
        assertEquals(2, result.cumulativeDays());
        assertEquals(2, result.consecutiveDays());
        assertTrue(result.memberToday());
    }

    private static MembershipProgress.Span span(Long storeId, String start, String end) {
        return new MembershipProgress.Span(storeId, LocalDateTime.parse(start), LocalDateTime.parse(end));
    }
}
