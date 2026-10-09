package com.gym.self;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.gate.GateService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LaterStagesTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TimeProvider timeProvider;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void gateGrouponCourseAndTickets() throws Exception {
        timeProvider.use(Clock.fixed(LocalDateTime.of(2026, 10, 8, 12, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), TimeProvider.ZONE));
        try {
            String master = login("admin", "admin123");
            JsonNode agreement = publishAgreement(master);
            String storeId = createStore(master, "GATE1");
            String otherId = createStore(master, "GATE2");
            String cardId = createCard(master, storeId, "闸机周卡", 700, 7);
            String access = enroll("13700004444");
            String memberNo = me(access).get("memberNo").asText();
            String paid = createOrder(access, storeId, cardId, agreement, "gate-week");
            mockMvc.perform(post("/api/mp/orders/" + paid + "/mock-pay").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/admin/gates")
                            .header("Authorization", "Bearer " + master)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":\"" + storeId + "\",\"name\":\"正门\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/admin/gates")
                            .header("Authorization", "Bearer " + master)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":\"" + otherId + "\",\"name\":\"侧门\"}"))
                    .andExpect(status().isOk());
            JsonNode gates = read(mockMvc.perform(get("/api/admin/gates").header("Authorization", "Bearer " + master))
                    .andExpect(status().isOk()).andReturn());
            JsonNode own = null;
            JsonNode other = null;
            for (JsonNode gate : gates) {
                if (storeId.equals(gate.get("storeId").asText())) {
                    own = gate;
                }
                if (otherId.equals(gate.get("storeId").asText())) {
                    other = gate;
                }
            }
            verify(own, memberNo, true, "开门");
            verify(own, memberNo, true, "开门");
            long success = membershipMapper.selectCount(new LambdaQueryWrapper<Membership>().eq(Membership::getUserId, userId(access)));
            assertEquals(1, success);
            MvcResult doors = mockMvc.perform(get("/api/admin/doors").param("storeId", storeId)
                            .header("Authorization", "Bearer " + master))
                    .andExpect(status().isOk()).andReturn();
            JsonNode doorRows = objectMapper.readTree(doors.getResponse().getContentAsString()).get("data");
            int opened = 0;
            for (JsonNode row : doorRows) {
                if ("SUCCESS".equals(row.get("result").asText()) && memberNo.equals(row.get("memberNo").asText())) {
                    opened++;
                    assertEquals("已采集", row.get("face").asText());
                }
            }
            assertEquals(1, opened);
            verify(other, memberNo, false, "非本店会员");
            verify(own, "lesson:qr:1", false, "未注册");

            timeProvider.use(Clock.fixed(LocalDateTime.of(2026, 10, 16, 12, 0)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), TimeProvider.ZONE));
            verify(own, memberNo, false, "会员无效");

            timeProvider.use(Clock.fixed(LocalDateTime.of(2026, 10, 8, 12, 0)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), TimeProvider.ZONE));
            mockMvc.perform(post("/api/admin/groupon-rules")
                            .header("Authorization", "Bearer " + master)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","platform":"MEITUAN","code":"COUPON-GATE","cardProductId":"%s"}
                                    """.formatted(storeId, cardId)))
                    .andExpect(status().isOk());
            String second = enroll("13700005555");
            mockMvc.perform(post("/api/mp/groupon/redeem")
                            .header("Authorization", "Bearer " + second)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","platform":"MEITUAN","code":"NO-SUCH"}
                                    """.formatted(storeId)))
                    .andExpect(jsonPath("$.code").value(41000));
            mockMvc.perform(post("/api/mp/groupon/redeem")
                            .header("Authorization", "Bearer " + second)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","platform":"MEITUAN","code":"COUPON-GATE"}
                                    """.formatted(storeId)))
                    .andExpect(status().isOk());
            Membership granted = membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                    .eq(Membership::getUserId, userId(second))
                    .eq(Membership::getSource, "GROUPON"));
            assertEquals(LocalDateTime.of(2026, 10, 15, 12, 0), granted.getEndAt());

            mockMvc.perform(post("/api/mp/repairs")
                            .header("Authorization", "Bearer " + access)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","content":"跑步机异响","equipmentCode":"T1"}
                                    """.formatted(storeId)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/mp/repairs")
                            .header("Authorization", "Bearer " + access)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","content":"违禁内容"}
                                    """.formatted(storeId)))
                    .andExpect(jsonPath("$.code").value(41000));
            String clerk = loginAfterCreate(master, "clerk-gate", storeId);
            mockMvc.perform(get("/api/admin/repairs").param("storeId", otherId).header("Authorization", "Bearer " + clerk))
                    .andExpect(status().isForbidden());
            assertFalse(read(mockMvc.perform(get("/api/admin/repairs").header("Authorization", "Bearer " + clerk))
                    .andExpect(status().isOk()).andReturn()).isEmpty());

            String coachId = read(mockMvc.perform(post("/api/admin/coaches")
                            .header("Authorization", "Bearer " + master)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","name":"教练甲","phone":"13700006666","intro":"私教"}
                                    """.formatted(storeId)))
                    .andExpect(status().isOk()).andReturn()).get("id").asText();
            String packId = read(mockMvc.perform(post("/api/admin/packs")
                            .header("Authorization", "Bearer " + master)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"coachId":"%s","name":"十次课","priceFen":1000,"lessonCount":10,"content":"力量","audience":"新手"}
                                    """.formatted(coachId)))
                    .andExpect(status().isOk()).andReturn()).get("id").asText();
            MvcResult courseOrder = mockMvc.perform(post("/api/mp/packs/" + packId + "/orders")
                            .header("Authorization", "Bearer " + access)
                            .header("Idempotency-Key", "course-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"agreementId":"%s","agreementVersion":%d}
                                    """.formatted(agreement.get("id").asText(), agreement.get("versionNo").asInt())))
                    .andExpect(status().isOk()).andReturn();
            String courseId = objectMapper.readTree(courseOrder.getResponse().getContentAsString()).get("data").get("orderId").asText();
            mockMvc.perform(post("/api/mp/orders/" + courseId + "/mock-pay").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());
            long cards = membershipMapper.selectCount(new LambdaQueryWrapper<Membership>().eq(Membership::getOrderId, Long.parseLong(courseId)));
            assertEquals(0, cards);
            mockMvc.perform(get("/api/mp/lessons").header("Authorization", "Bearer " + access))
                    .andExpect(jsonPath("$.data[0].remaining").value(10));
        } finally {
            timeProvider.reset();
        }
    }

    private void verify(JsonNode gate, String memberNo, boolean open, String reason) throws Exception {
        String body = "{\"memberNo\":\"" + memberNo + "\"}";
        String timestamp = String.valueOf(timeProvider.now().atZone(TimeProvider.ZONE).toInstant().toEpochMilli());
        String nonce = UUID.randomUUID().toString();
        String signature = GateService.sign(gate.get("secret").asText(), gate.get("deviceSn").asText(), timestamp, nonce, body);
        mockMvc.perform(post("/api/gate/v1/verify")
                        .header("X-Device-Sn", gate.get("deviceSn").asText())
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", nonce)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(open))
                .andExpect(jsonPath("$.data.reason").value(reason));
    }

    private JsonNode me(String access) throws Exception {
        return read(mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk()).andReturn());
    }

    private long userId(String access) throws Exception {
        return Long.parseLong(objectMapper.readTree(new String(java.util.Base64.getUrlDecoder()
                .decode(access.split("\\.")[1]))).get("sub").asText());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode publishAgreement(String master) throws Exception {
        mockMvc.perform(put("/api/admin/agreements/1")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"会员协议\",\"content\":\"购买前请阅读\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/agreements/1/publish").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk());
        return read(mockMvc.perform(get("/api/mp/agreements/current")).andExpect(status().isOk()).andReturn());
    }

    private String createOrder(String access, String storeId, String cardId, JsonNode agreement, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mp/orders")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content("""
                                {"storeId":"%s","cardProductId":"%s","agreementId":"%s","agreementVersion":%d}
                                """.formatted(storeId, cardId, agreement.get("id").asText(), agreement.get("versionNo").asInt())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("orderId").asText();
    }

    private String createCard(String token, String storeId, String name, int priceFen, int days) throws Exception {
        return read(mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":"%s","name":"%s","priceFen":%d,"validDays":%d,"durationMode":"HOURS_24","unlockType":"NONE","crossStore":0,"homeVisible":1,"sortNo":1,"status":"ON"}
                                """.formatted(storeId, name, priceFen, days)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private String createStore(String token, String code) throws Exception {
        return read(mockMvc.perform(post("/api/admin/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"闸机店","province":"天津市","city":"天津市","address":"测试路","longitude":117.2,"latitude":39.1}
                                """.formatted(code)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
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
        return read(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }

    private String enroll(String phone) throws Exception {
        String sessionToken = read(mockMvc.perform(post("/api/mp/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"later-" + phone + "\"}"))
                .andExpect(status().isOk()).andReturn()).get("sessionToken").asText();
        String face = read(mockMvc.perform(post("/api/mp/auth/phone")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneCode\":\"" + phone + "\"}"))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
        String objectKey = read(mockMvc.perform(post("/api/mp/files/presign")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biz\":\"FACE\",\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk()).andReturn()).get("objectKey").asText();
        mockMvc.perform(post("/api/mp/files/upload")
                        .header("Authorization", "Bearer " + face)
                        .header("X-Object-Key", objectKey)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[64]))
                .andExpect(status().isOk());
        return read(mockMvc.perform(post("/api/mp/face")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }
}
