package com.gym.self.modules.lesson.application;

import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LessonNotify {

    private final JdbcTemplate jdbc;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public LessonNotify(JdbcTemplate jdbc, Snowflake snowflake, TimeProvider timeProvider) {
        this.jdbc = jdbc;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    /** 用户收件箱（可选）+ 管理端审计（必写） */
    public void send(Long userId, String title, String content, String bizType, Long bizId, Long storeId, Long coachId) {
        String t = truncate(title, 64);
        String c = truncate(content, 500);
        jdbc.update("""
                INSERT INTO message_audit (id, title, content, biz_type, biz_id, store_id, coach_id, user_id, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, snowflake.next(), t, c, bizType, bizId, storeId, coachId, userId, timeProvider.now());
        if (userId == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO inbox_message (id, user_id, title, content, created_at, biz_type, biz_id, store_id, coach_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, snowflake.next(), userId, t, c, timeProvider.now(), bizType, bizId, storeId, coachId);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String t = value.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
