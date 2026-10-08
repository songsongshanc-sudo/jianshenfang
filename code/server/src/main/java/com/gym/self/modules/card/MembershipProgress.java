package com.gym.self.modules.card;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MembershipProgress {

    private MembershipProgress() {
    }

    public record Span(Long storeId, LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record Result(int cumulativeDays, int consecutiveDays, boolean memberToday) {
    }

    public static Result of(List<Span> spans, LocalDate today, Long storeId) {
        Set<LocalDate> covered = new HashSet<>();
        boolean memberToday = false;
        for (Span span : spans) {
            if (span.startAt() == null || span.endAt() == null || !span.endAt().isAfter(span.startAt())) {
                continue;
            }
            LocalDate from = span.startAt().toLocalDate();
            LocalDate to = span.endAt().toLocalDate();
            if (span.endAt().equals(to.atStartOfDay())) {
                to = to.minusDays(1);
            }
            if (storeId != null && storeId.equals(span.storeId()) && !from.isAfter(today) && !to.isBefore(today)) {
                memberToday = true;
            }
            if (to.isAfter(today)) {
                to = today;
            }
            if (from.isAfter(to)) {
                continue;
            }
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                covered.add(day);
            }
        }
        int consecutive = 0;
        for (LocalDate day = today; covered.contains(day); day = day.minusDays(1)) {
            consecutive++;
        }
        return new Result(covered.size(), consecutive, memberToday);
    }
}
