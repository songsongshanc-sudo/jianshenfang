package com.gym.self;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthAndMigrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthReturnsOkEnvelope() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void adminPingWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void flywayCreatesSeedData() {
        Integer admins = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user WHERE username = 'admin' AND role = 'MASTER'", Integer.class);
        String debounce = jdbcTemplate.queryForObject(
                "SELECT config_value FROM app_config WHERE config_key = 'entry.debounce.seconds'", String.class);
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM admin_user WHERE username = 'admin'", String.class);
        assertThat(admins).isEqualTo(1);
        assertThat(debounce).isEqualTo("15");
        assertThat(new BCryptPasswordEncoder().matches("admin123", hash)).isTrue();
    }
}
