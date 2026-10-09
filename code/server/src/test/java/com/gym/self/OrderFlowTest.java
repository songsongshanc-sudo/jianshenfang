package com.gym.self;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TimeProvider timeProvider;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void payOnceExtendsMembershipAndRefundTruncates() throws Exception {
        timeProvider.use(Clock.fixed(LocalDateTime.of(2026, 10, 8, 12, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), TimeProvider.ZONE));
        try {
            String master = login("admin", "admin123");
            JsonNode agreement = publishAgreement(master);
            String storeId = createStore(master, "ORD1");
            String otherStore = createStore(master, "ORD2");
            String limited = createCard(master, storeId, "限量次卡", 100, 1, 1);
            String week = createCard(master, storeId, "周卡订单", 700, 7, null);
            String clerk = loginAfterCreate(master, "clerk-order", storeId);
            String access = enroll("13900003333");

            mockMvc.perform(post("/api/mp/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "guest-1")
                            .content(orderBody(storeId, limited, agreement)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/mp/orders")
                            .header("Authorization", "Bearer " + access)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "bad-agreement")
                            .content(orderBody(storeId, limited, agreement.get("id").asText(), 999)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(41000));

            String sameKey = "same-key-1";
            String firstId = createOrder(access, storeId, limited, agreement, sameKey);
            String againId = createOrder(access, storeId, limited, agreement, sameKey);
            assertEquals(firstId, againId);
            mockMvc.perform(post("/api/mp/orders/" + firstId + "/cancel")
                            .header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));

            List<Integer> codes = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            for (int i = 0; i < 2; i++) {
                int index = i;
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        go.await(10, TimeUnit.SECONDS);
                        MvcResult result = mockMvc.perform(post("/api/mp/orders")
                                        .header("Authorization", "Bearer " + access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "race-" + index)
                                        .content(orderBody(storeId, limited, agreement)))
                                .andReturn();
                        codes.add(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt());
                    } catch (Exception exception) {
                        codes.add(-1);
                    }
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
            assertTrue(codes.contains(0));
            assertTrue(codes.contains(41000));

            String paidId = createOrder(access, storeId, week, agreement, "week-1");
            mockMvc.perform(post("/api/mp/orders/" + paidId + "/mock-pay")
                            .header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAID"));
            mockMvc.perform(post("/api/mp/orders/" + paidId + "/mock-pay")
                            .header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAID"));
            long userId = membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                    .eq(Membership::getOrderId, Long.parseLong(paidId))).getUserId();
            assertEquals(1, membershipMapper.selectCount(new LambdaQueryWrapper<Membership>()
                    .eq(Membership::getUserId, userId)));
            mockMvc.perform(get("/api/mp/me").param("storeId", storeId).header("Authorization", "Bearer " + access))
                    .andExpect(jsonPath("$.data.storeMember").value(true));

            String secondId = createOrder(access, storeId, week, agreement, "week-2");
            mockMvc.perform(post("/api/mp/orders/" + secondId + "/mock-pay")
                            .header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());
            Membership second = membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                    .eq(Membership::getOrderId, Long.parseLong(secondId)));
            assertEquals(LocalDateTime.of(2026, 10, 15, 12, 0), second.getStartAt());
            assertEquals(LocalDateTime.of(2026, 10, 22, 12, 0), second.getEndAt());

            mockMvc.perform(post("/api/admin/orders/" + paidId + "/refund")
                            .header("Authorization", "Bearer " + clerk))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(41000))
                    .andExpect(jsonPath("$.message").value("购买后不能退款"));
            mockMvc.perform(post("/api/admin/orders/" + paidId + "/refund")
                            .header("Authorization", "Bearer " + master))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("购买后不能退款"));
            Membership kept = membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                    .eq(Membership::getOrderId, Long.parseLong(paidId)));
            assertEquals(LocalDateTime.of(2026, 10, 15, 12, 0), kept.getEndAt());

            mockMvc.perform(get("/api/admin/orders").param("storeId", otherStore)
                            .header("Authorization", "Bearer " + clerk))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/orders")
                            .header("Authorization", "Bearer " + clerk))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].storeName").value("订单店"));
        } finally {
            timeProvider.reset();
        }
    }

    private JsonNode publishAgreement(String master) throws Exception {
        MvcResult current = mockMvc.perform(get("/api/mp/agreements/current")).andReturn();
        JsonNode data = objectMapper.readTree(current.getResponse().getContentAsString()).get("data");
        if (data != null && !data.isNull()) {
            return data;
        }
        mockMvc.perform(put("/api/admin/agreements/1")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"会员协议\",\"content\":\"购买前请阅读\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/agreements/1/publish").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk());
        MvcResult published = mockMvc.perform(get("/api/mp/agreements/current")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(published.getResponse().getContentAsString()).get("data");
    }

    private String createOrder(String access, String storeId, String cardId, JsonNode agreement, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mp/orders")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(orderBody(storeId, cardId, agreement)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("orderId").asText();
    }

    private static String orderBody(String storeId, String cardId, JsonNode agreement) {
        return orderBody(storeId, cardId, agreement.get("id").asText(), agreement.get("versionNo").asInt());
    }

    private static String orderBody(String storeId, String cardId, String agreementId, int version) {
        return """
                {"storeId":"%s","cardProductId":"%s","agreementId":"%s","agreementVersion":%d}
                """.formatted(storeId, cardId, agreementId, version);
    }

    private String createCard(String token, String storeId, String name, int priceFen, int days, Integer stock) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":"%s","name":"%s","priceFen":%d,"validDays":%d,"durationMode":"HOURS_24","stockTotal":%s,"unlockType":"NONE","crossStore":0,"homeVisible":1,"sortNo":1,"status":"ON"}
                                """.formatted(storeId, name, priceFen, days, stock == null ? "null" : stock)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String createStore(String token, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"订单店","province":"天津市","city":"天津市","address":"测试路","longitude":117.2,"latitude":39.1}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String loginAfterCreate(String master, String username, String storeId) throws Exception {
        mockMvc.perform(post("/api/admin/accounts")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"pass1234","storeId":"%s"}
                                """.formatted(username, storeId)))
                .andExpect(status().isOk());
        return login(username, "pass1234");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }

    private String enroll(String phone) throws Exception {
        MvcResult session = mockMvc.perform(post("/api/mp/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"order-" + phone + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String sessionToken = objectMapper.readTree(session.getResponse().getContentAsString()).get("data").get("sessionToken").asText();
        MvcResult phoneResult = mockMvc.perform(post("/api/mp/auth/phone")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneCode\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String face = objectMapper.readTree(phoneResult.getResponse().getContentAsString()).get("data").get("accessToken").asText();
        MvcResult presign = mockMvc.perform(post("/api/mp/files/presign")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biz\":\"FACE\",\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String objectKey = objectMapper.readTree(presign.getResponse().getContentAsString()).get("data").get("objectKey").asText();
        mockMvc.perform(post("/api/mp/files/upload")
                        .header("Authorization", "Bearer " + face)
                        .header("X-Object-Key", objectKey)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[64]))
                .andExpect(status().isOk());
        MvcResult enrolled = mockMvc.perform(post("/api/mp/face")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }
}
