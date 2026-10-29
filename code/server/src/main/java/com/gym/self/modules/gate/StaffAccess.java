package com.gym.self.modules.gate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** 教练和进门管理员的闸机到期时间。会员卡到期之外的进门资格。 */
@Service
public class StaffAccess {

    private final JdbcTemplate jdbc;

    public StaffAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDateTime until(long userId, long storeId) {
        List<Timestamp> rows = jdbc.query("""
                SELECT MAX(gate_until) FROM (
                  SELECT c.gate_until FROM coach c
                  JOIN `user` u ON u.phone = c.phone
                  WHERE u.id = ? AND c.store_id = ? AND c.gate_until IS NOT NULL
                  UNION ALL
                  SELECT d.gate_until FROM door_staff d
                  WHERE d.user_id = ? AND d.store_id = ? AND d.gate_until IS NOT NULL
                ) t
                """, (rs, row) -> rs.getTimestamp(1), userId, storeId, userId, storeId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        return rows.get(0).toLocalDateTime();
    }

    public boolean open(long userId, long storeId, LocalDateTime now) {
        LocalDateTime until = until(userId, storeId);
        return until != null && until.isAfter(now);
    }

    public List<Long> storeIds(long userId) {
        return jdbc.query("""
                SELECT store_id FROM coach c
                JOIN `user` u ON u.phone = c.phone
                WHERE u.id = ? AND c.gate_until IS NOT NULL
                UNION
                SELECT store_id FROM door_staff WHERE user_id = ? AND gate_until IS NOT NULL
                """, (rs, row) -> rs.getLong(1), userId, userId);
    }

    public List<Long> userIds(long storeId, LocalDateTime now) {
        return jdbc.query("""
                SELECT u.id FROM coach c
                JOIN `user` u ON u.phone = c.phone
                WHERE c.store_id = ? AND c.gate_until > ?
                UNION
                SELECT user_id FROM door_staff WHERE store_id = ? AND gate_until > ?
                """, (rs, row) -> rs.getLong(1), storeId, Timestamp.valueOf(now), storeId, Timestamp.valueOf(now));
    }
}
