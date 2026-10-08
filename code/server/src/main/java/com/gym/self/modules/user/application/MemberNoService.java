package com.gym.self.modules.user.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberNoService {

    private final JdbcTemplate jdbcTemplate;

    public MemberNoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String next() {
        jdbcTemplate.update("UPDATE member_no_seq SET next_no = next_no + 1 WHERE id = 1");
        Long current = jdbcTemplate.queryForObject("SELECT next_no FROM member_no_seq WHERE id = 1", Long.class);
        return String.format("%09d", current - 1);
    }
}
