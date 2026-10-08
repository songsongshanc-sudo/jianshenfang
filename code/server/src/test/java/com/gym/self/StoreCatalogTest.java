package com.gym.self;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoreCatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TimeProvider timeProvider;

    @Test
    void masterConfiguresStoresAndNightPhonesFollowServerClock() throws Exception {
        String master = login("admin", "admin123");
        String hebei = createStore(master, "S301", "河北店", "河北省", "石家庄市", "114.500000", "38.000000");
        String shanghai = createStore(master, "S302", "上海店", "上海市", "上海市", "121.400000", "31.200000");

        mockMvc.perform(put("/api/admin/stores/" + hebei)
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"河北店","province":"河北省","city":"石家庄市","address":"测试路1号","longitude":114.5,"latitude":38.0,"coverUrl":"https://img.example/cover.jpg","businessHours":"24h","status":"OPEN","wifiSsid":"gym-wifi","wifiPassword":"wifi-secret-z9"}
                                """))
                .andExpect(status().isOk());

        String phones = """
                {"items":[
                  {"phoneType":"DAY","phone":"10000000001","timeStart":"09:00","timeEnd":"21:30","sortNo":1},
                  {"phoneType":"NIGHT","phone":"10000000002","timeStart":"21:30","timeEnd":"09:00","sortNo":2},
                  {"phoneType":"LOGISTICS","phone":"10000000004","sortNo":3},
                  {"phoneType":"COMPLAINT","phone":"10000000003","timeStart":"09:00","timeEnd":"18:00","sortNo":4}
                ]}
                """;
        mockMvc.perform(put("/api/admin/stores/" + hebei + "/phones")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phones))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/stores/" + hebei + "/guides")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk());

        String clerk = loginAfterCreate(master, "clerk-s3", hebei);
        mockMvc.perform(put("/api/admin/stores/" + hebei)
                        .header("Authorization", "Bearer " + clerk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"河北店","province":"河北省","city":"石家庄市","address":"测试路1号","longitude":114.5,"latitude":38.0,"status":"OPEN"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        MvcResult listed = mockMvc.perform(get("/api/mp/stores")
                        .param("province", "河北省")
                        .param("city", "石家庄市")
                        .param("longitude", "114.52")
                        .param("latitude", "38.05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode stores = objectMapper.readTree(listed.getResponse().getContentAsString()).get("data");
        List<String> ids = new ArrayList<>();
        stores.forEach(node -> ids.add(node.get("id").asText()));
        assertTrue(ids.contains(hebei));
        assertFalse(ids.contains(shanghai));
        assertEquals(hebei, stores.get(0).get("id").asText());
        assertFalse(listed.getResponse().getContentAsString().contains("wifi-secret-z9"));
        assertFalse(listed.getResponse().getContentAsString().contains("wifiPassword"));

        MvcResult detail = mockMvc.perform(get("/api/mp/stores/" + hebei))
                .andExpect(status().isOk())
                .andReturn();
        String detailBody = detail.getResponse().getContentAsString();
        assertFalse(detailBody.contains("wifi-secret-z9"));
        assertFalse(detailBody.contains("wifiSsid"));
        assertTrue(detailBody.contains("https://img.example/cover.jpg"));
        assertTrue(detailBody.contains("开通会员后查看"));

        mockMvc.perform(get("/api/mp/stores/" + hebei + "/guides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        try {
            assertShift(hebei, at(21, 29), "DAY", "10000000001", false);
            assertShift(hebei, at(21, 30), "NIGHT", "10000000002", true);
            assertShift(hebei, at(0, 10), "NIGHT", "10000000002", true);
            assertShift(hebei, at(9, 0), "DAY", "10000000001", false);
        } finally {
            timeProvider.reset();
        }

        mockMvc.perform(put("/api/admin/agreements/1")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"会员协议\",\"content\":\"购买前请阅读本协议\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/agreements/1/publish").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/agreements/1")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"会员协议\",\"content\":\"不能再改\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(get("/api/mp/agreements/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.content").value("购买前请阅读本协议"));
        mockMvc.perform(get("/api/admin/configs").header("Authorization", "Bearer " + clerk))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/configs").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundDailyDeductFen").value(""))
                .andExpect(jsonPath("$.data.entryDebounceSeconds").value(15));
    }

    private void assertShift(String storeId, Clock clock, String shift, String phone, boolean night) throws Exception {
        timeProvider.use(clock);
        mockMvc.perform(get("/api/mp/stores/" + storeId + "/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shift").value(shift))
                .andExpect(jsonPath("$.data.nightAvailable").value(night))
                .andExpect(jsonPath("$.data.servicePhones[0]").value(phone))
                .andExpect(jsonPath("$.data.logisticsPhones[0]").value("10000000004"));
    }

    private static Clock at(int hour, int minute) {
        return Clock.fixed(LocalDateTime.of(2026, 10, 8, hour, minute).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                TimeProvider.ZONE);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }

    private String loginAfterCreate(String master, String username, String storeId) throws Exception {
        String body = """
                {"username":"%s","password":"pass1234","storeId":"%s"}
                """.formatted(username, storeId);
        mockMvc.perform(post("/api/admin/accounts")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        return login(username, "pass1234");
    }

    private String createStore(String token, String code, String name, String province, String city,
                               String longitude, String latitude) throws Exception {
        String body = """
                {"code":"%s","name":"%s","province":"%s","city":"%s","address":"测试地址","longitude":%s,"latitude":%s}
                """.formatted(code, name, province, city, longitude, latitude);
        MvcResult result = mockMvc.perform(post("/api/admin/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }
}
