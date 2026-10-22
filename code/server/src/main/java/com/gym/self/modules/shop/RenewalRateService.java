package com.gym.self.modules.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一笔购课的节数扣光后，再买同一教练的课算续课成功。课时没上完又买，不算。
 */
@Service
public class RenewalRateService {

    private final JdbcTemplate jdbc;

    public RenewalRateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> summary(long storeId, LocalDate from, LocalDate to) {
        List<Lot> lots = lots(storeId, null);
        Map<Long, Map<String, Object>> byCoach = new HashMap<>();
        for (Lot lot : lots) {
            if (!inRange(lot.finishedAt, from, to)) {
                continue;
            }
            Map<String, Object> row = byCoach.computeIfAbsent(lot.coachId, id -> {
                Map<String, Object> created = new HashMap<>();
                created.put("coachId", String.valueOf(id));
                created.put("coachName", lot.coachName);
                created.put("finished", 0);
                created.put("renewed", 0);
                return created;
            });
            row.put("finished", ((Integer) row.get("finished")) + 1);
            if (lot.renewed) {
                row.put("renewed", ((Integer) row.get("renewed")) + 1);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(byCoach.values());
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("coachName"))));
        for (Map<String, Object> row : rows) {
            int finished = (Integer) row.get("finished");
            int renewed = (Integer) row.get("renewed");
            row.put("rate", finished == 0 ? null : renewed * 100.0 / finished);
        }
        return rows;
    }

    public List<Map<String, Object>> detail(long coachId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Lot lot : lots(null, coachId)) {
            if (!inRange(lot.finishedAt, from, to)) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("memberName", lot.memberName);
            row.put("phone", lot.phone);
            row.put("packName", lot.packName);
            row.put("finishedAt", lot.finishedAt);
            row.put("renewed", lot.renewed);
            row.put("renewedAt", lot.renewedAt);
            row.put("renewPackName", lot.renewPackName);
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> (LocalDateTime) row.get("finishedAt")).reversed());
        return rows;
    }

    private List<Lot> lots(Long storeId, Long coachId) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.user_id, o.product_id AS pack_id, o.paid_at, p.coach_id, p.name AS pack_name,
                       p.lesson_count, c.name AS coach_name, u.nickname, u.phone
                FROM trade_order o
                JOIN course_pack p ON p.id = o.product_id
                JOIN coach c ON c.id = p.coach_id
                JOIN `user` u ON u.id = o.user_id
                WHERE o.biz_type = 'COURSE' AND o.status = 'PAID'
                """);
        List<Object> args = new ArrayList<>();
        if (storeId != null) {
            sql.append(" AND c.store_id = ?");
            args.add(storeId);
        }
        if (coachId != null) {
            sql.append(" AND c.id = ?");
            args.add(coachId);
        }
        sql.append(" ORDER BY o.paid_at, o.id");
        List<Map<String, Object>> orders = jdbc.queryForList(sql.toString(), args.toArray());
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = orders.stream().map(row -> ((Number) row.get("user_id")).longValue()).distinct().toList();
        String marks = String.join(",", userIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> ledger = jdbc.queryForList(
                "SELECT user_id, pack_id, delta, reason, created_at FROM lesson_ledger WHERE user_id IN (" + marks
                        + ") ORDER BY created_at, id",
                userIds.toArray());

        Map<String, List<Lot>> open = new HashMap<>();
        List<Lot> all = new ArrayList<>();
        Map<String, Integer> payCursor = new HashMap<>();
        Map<String, List<Map<String, Object>>> pays = new HashMap<>();
        for (Map<String, Object> row : ledger) {
            if (!"PAY".equals(String.valueOf(row.get("reason")))) {
                continue;
            }
            pays.computeIfAbsent(key(row.get("user_id"), row.get("pack_id")), ignored -> new ArrayList<>()).add(row);
        }
        for (Map<String, Object> order : orders) {
            String key = key(order.get("user_id"), order.get("pack_id"));
            List<Map<String, Object>> packPays = pays.getOrDefault(key, List.of());
            int index = payCursor.getOrDefault(key, 0);
            int lessons = ((Number) order.get("lesson_count")).intValue();
            if (index < packPays.size()) {
                lessons = Math.max(1, ((Number) packPays.get(index).get("delta")).intValue());
                payCursor.put(key, index + 1);
            }
            Lot lot = new Lot(
                    ((Number) order.get("user_id")).longValue(),
                    ((Number) order.get("coach_id")).longValue(),
                    String.valueOf(order.get("coach_name")),
                    String.valueOf(order.get("pack_name")),
                    order.get("nickname") == null ? "" : String.valueOf(order.get("nickname")),
                    order.get("phone") == null ? "" : String.valueOf(order.get("phone")),
                    toTime(order.get("paid_at")),
                    lessons);
            open.computeIfAbsent(key, ignored -> new ArrayList<>()).add(lot);
            all.add(lot);
        }
        Map<String, List<Map<String, Object>>> moves = new HashMap<>();
        for (Map<String, Object> row : ledger) {
            if ("PAY".equals(String.valueOf(row.get("reason")))) {
                continue;
            }
            moves.computeIfAbsent(key(row.get("user_id"), row.get("pack_id")), ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : moves.entrySet()) {
            List<Lot> queue = open.getOrDefault(entry.getKey(), List.of());
            for (Map<String, Object> move : entry.getValue()) {
                int delta = ((Number) move.get("delta")).intValue();
                LocalDateTime at = toTime(move.get("created_at"));
                if (delta < 0) {
                    int left = -delta;
                    for (Lot lot : queue) {
                        if (left == 0) {
                            break;
                        }
                        int room = lot.lessons - lot.consumed;
                        if (room <= 0) {
                            continue;
                        }
                        int take = Math.min(room, left);
                        lot.consumed += take;
                        left -= take;
                        if (lot.consumed >= lot.lessons && lot.finishedAt == null) {
                            lot.finishedAt = at;
                        }
                    }
                } else if (delta > 0) {
                    int left = delta;
                    for (int i = queue.size() - 1; i >= 0 && left > 0; i--) {
                        Lot lot = queue.get(i);
                        int back = Math.min(lot.consumed, left);
                        lot.consumed -= back;
                        left -= back;
                        if (lot.consumed < lot.lessons) {
                            lot.finishedAt = null;
                        }
                    }
                }
            }
        }
        markRenewals(all);
        return all;
    }

    private void markRenewals(List<Lot> all) {
        Map<String, List<Lot>> byMemberCoach = new HashMap<>();
        for (Lot lot : all) {
            byMemberCoach.computeIfAbsent(lot.userId + ":" + lot.coachId, ignored -> new ArrayList<>()).add(lot);
        }
        for (List<Lot> group : byMemberCoach.values()) {
            group.sort(Comparator.comparing(lot -> lot.boughtAt));
            for (Lot purchase : group) {
                Lot target = null;
                for (Lot finished : group) {
                    if (finished == purchase || finished.finishedAt == null || finished.renewed) {
                        continue;
                    }
                    if (!finished.finishedAt.isBefore(purchase.boughtAt)) {
                        continue;
                    }
                    if (target == null || finished.finishedAt.isAfter(target.finishedAt)) {
                        target = finished;
                    }
                }
                if (target != null) {
                    target.renewed = true;
                    target.renewedAt = purchase.boughtAt;
                    target.renewPackName = purchase.packName;
                }
            }
        }
    }

    private static boolean inRange(LocalDateTime finishedAt, LocalDate from, LocalDate to) {
        if (finishedAt == null) {
            return false;
        }
        LocalDate day = finishedAt.toLocalDate();
        if (from != null && day.isBefore(from)) {
            return false;
        }
        return to == null || !day.isAfter(to);
    }

    private static String key(Object userId, Object packId) {
        return userId + ":" + packId;
    }

    private static LocalDateTime toTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private static final class Lot {
        private final long userId;
        private final long coachId;
        private final String coachName;
        private final String packName;
        private final String memberName;
        private final String phone;
        private final LocalDateTime boughtAt;
        private final int lessons;
        private int consumed;
        private LocalDateTime finishedAt;
        private boolean renewed;
        private LocalDateTime renewedAt;
        private String renewPackName;

        private Lot(long userId, long coachId, String coachName, String packName, String memberName,
                    String phone, LocalDateTime boughtAt, int lessons) {
            this.userId = userId;
            this.coachId = coachId;
            this.coachName = coachName;
            this.packName = packName;
            this.memberName = memberName;
            this.phone = phone;
            this.boughtAt = boughtAt;
            this.lessons = lessons;
        }
    }
}
