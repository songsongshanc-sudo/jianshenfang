package com.gym.self;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.gate.GateFaceSync;
import com.gym.self.modules.gate.GateSignature;
import com.gym.self.modules.gate.WsGateCommandPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VendorGateTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TimeProvider timeProvider;
    @Autowired
    private GateFaceSync gateFaceSync;
    @MockitoBean
    private WsGateCommandPort port;

    @Test
    void signatureExampleMatchesVendorDoc() {
        assertEquals("29715b4006f3a129482a96e12814be5a9b9ce04c",
                GateSignature.sha1("T123456", "toekn123", "1680083968", "6666"));
    }

    @Test
    void vendorFlowPushesFaceAndDecidesDoor() throws Exception {
        timeProvider.use(Clock.fixed(LocalDateTime.of(2026, 10, 8, 12, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), TimeProvider.ZONE));
        try {
            String master = login("admin", "admin123");
            JsonNode agreement = publishAgreement(master);
            String storeId = createStore(master, "VG" + UUID.randomUUID().toString().substring(0, 4));
            String otherId = createStore(master, "VX" + UUID.randomUUID().toString().substring(0, 4));
            String cardId = createCard(master, storeId);
            String access = enroll("139" + (10000000 + Math.floorMod(UUID.randomUUID().hashCode(), 90000000)));
            String memberNo = me(access, storeId).get("memberNo").asText();
            String paid = createOrder(access, storeId, cardId, agreement);
            mockMvc.perform(post("/api/mp/orders/" + paid + "/mock-pay").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());
            String ownSn = "GT" + UUID.randomUUID().toString().substring(0, 8);
            String otherSn = "GO" + UUID.randomUUID().toString().substring(0, 8);
            mockMvc.perform(post("/api/admin/gates").header("Authorization", "Bearer " + master)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","name":"正门","deviceSn":"%s"}
                                    """.formatted(storeId, ownSn)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/admin/gates").header("Authorization", "Bearer " + master)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeId":"%s","name":"侧门","deviceSn":"%s"}
                                    """.formatted(otherId, otherSn)))
                    .andExpect(status().isOk());
            JsonNode own = findGate(master, ownSn);
            assertTrue(own.get("pendingCount").asInt() >= 1);
            assertEquals("PENDING", me(access, storeId).get("faceSync").asText());

            AtomicInteger adds = new AtomicInteger();
            when(port.exchange(anyString(), any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = invocation.getArgument(1);
                if ("addUser".equals(data.get("cmd"))) {
                    adds.incrementAndGet();
                    assertEquals(memberNo, data.get("user_id"));
                    assertTrue(String.valueOf(data.get("face_template")).contains("base64"));
                }
                ObjectNode reply = objectMapper.createObjectNode();
                reply.put("cmd", data.get("cmd") + "Ret");
                reply.put("code", 0);
                reply.put("msg", "");
                return reply;
            });
            when(port.online(anyString())).thenReturn(true);
            gateFaceSync.flushDevice(ownSn);
            assertTrue(adds.get() >= 1);
            assertEquals(0, findGate(master, ownSn).get("pendingCount").asInt());
            assertEquals("OK", me(access, storeId).get("faceSync").asText());

            String recog = "2026-10-08 12:00:00";
            verify(ownSn, memberNo, recog, null).andExpect(jsonPath("$.Result").value(0)).andExpect(jsonPath("$.Msg").value("开门"));
            verify(ownSn, memberNo, recog, null).andExpect(jsonPath("$.Result").value(0));
            assertEquals(1, countDoors(master, storeId, "SUCCESS", memberNo));
            mockMvc.perform(post("/api/v1/record/face").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sn":"%s","Count":1,"logs":[{"user_id":"%s","recog_time":"%s","recog_type":"face","pass_status":0}]}
                                    """.formatted(ownSn, memberNo, recog)))
                    .andExpect(jsonPath("$.Result").value(0));
            mockMvc.perform(post("/api/v1/record/face").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sn":"%s","Count":1,"logs":[{"user_id":"%s","recog_time":"%s","recog_type":"face","pass_status":0}]}
                                    """.formatted(ownSn, memberNo, recog)))
                    .andExpect(jsonPath("$.Result").value(0));
            assertEquals(1, countDoors(master, storeId, "SUCCESS", memberNo));
            mockMvc.perform(post("/api/v1/stranger").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sn":"%s","Count":1,"logs":[{"user_id":"F1000001","recog_time":"2026-10-08 12:01:00","recog_type":"face","pass_status":1}]}
                                    """.formatted(ownSn)))
                    .andExpect(jsonPath("$.Result").value(0));
            assertEquals(1, countDoors(master, storeId, "REJECTED", "陌生人"));
            verify(otherSn, memberNo, recog, null).andExpect(jsonPath("$.Result").value(3));
            verify("NO-SUCH-GATE", memberNo, recog, null).andExpect(jsonPath("$.Result").value(-2));
            verify(ownSn, "lesson:abc", recog, null).andExpect(jsonPath("$.Result").value(3));

            String token = read(mockMvc.perform(post("/api/admin/gates/" + own.get("id").asText() + "/token")
                    .header("Authorization", "Bearer " + master)).andExpect(status().isOk()).andReturn()).get("token").asText();
            verify(ownSn, memberNo, recog, null).andExpect(jsonPath("$.Result").value(3)).andExpect(jsonPath("$.Msg").value("签名无效"));
            String timestamp = String.valueOf(timeProvider.now().atZone(TimeProvider.ZONE).toEpochSecond());
            String nonce = UUID.randomUUID().toString();
            verify(ownSn, memberNo, recog, GateSignature.sha1(ownSn, token, timestamp, nonce), timestamp, nonce)
                    .andExpect(jsonPath("$.Result").value(0));

            timeProvider.use(Clock.fixed(LocalDateTime.of(2026, 10, 16, 12, 0)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), TimeProvider.ZONE));
            String later = String.valueOf(timeProvider.now().atZone(TimeProvider.ZONE).toEpochSecond());
            String laterNonce = UUID.randomUUID().toString();
            verify(ownSn, memberNo, "2026-10-16 12:00:00", GateSignature.sha1(ownSn, token, later, laterNonce), later, laterNonce)
                    .andExpect(jsonPath("$.Result").value(1));
        } finally {
            timeProvider.reset();
        }
    }

    private org.springframework.test.web.servlet.ResultActions verify(String sn, String userId, String recog, String signature) throws Exception {
        return verify(sn, userId, recog, signature, null, null);
    }

    private org.springframework.test.web.servlet.ResultActions verify(String sn, String userId, String recog, String signature,
                                                                      String timestamp, String nonce) throws Exception {
        var request = post("/api/v1/verify_user").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sn":"%s","user_id":"%s","type":0,"confidence":90,"recog_time":"%s"}
                        """.formatted(sn, userId, recog));
        if (signature != null) {
            request.header("iot_sn", sn).header("iot_timestamp", timestamp).header("iot_nonce", nonce).header("iot_signature", signature);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private int countDoors(String master, String storeId, String result, String memberNo) throws Exception {
        JsonNode rows = read(mockMvc.perform(get("/api/admin/doors").param("storeId", storeId)
                .header("Authorization", "Bearer " + master)).andExpect(status().isOk()).andReturn());
        int count = 0;
        for (JsonNode row : rows) {
            if (result.equals(row.get("result").asText()) && memberNo.equals(row.get("memberNo").asText())) {
                count++;
            }
        }
        return count;
    }

    private JsonNode findGate(String master, String sn) throws Exception {
        for (JsonNode gate : read(mockMvc.perform(get("/api/admin/gates").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk()).andReturn())) {
            if (sn.equals(gate.get("deviceSn").asText())) {
                return gate;
            }
        }
        throw new AssertionError("缺少闸机 " + sn);
    }

    private JsonNode me(String access, String storeId) throws Exception {
        return read(mockMvc.perform(get("/api/mp/me").param("storeId", storeId).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String createOrder(String access, String storeId, String cardId, JsonNode agreement) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mp/orders")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", "vg-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":"%s","cardProductId":"%s","agreementId":"%s","agreementVersion":%d}
                                """.formatted(storeId, cardId, agreement.get("id").asText(), agreement.get("versionNo").asInt())))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("orderId").asText();
    }

    private String createCard(String token, String storeId) throws Exception {
        return read(mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":"%s","name":"厂商周卡","priceFen":700,"validDays":7,"durationMode":"HOURS_24","unlockType":"NONE","crossStore":0,"homeVisible":1,"sortNo":1,"status":"ON"}
                                """.formatted(storeId)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private String createStore(String token, String code) throws Exception {
        return read(mockMvc.perform(post("/api/admin/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"厂商店","province":"天津市","city":"天津市","address":"测试路","longitude":117.2,"latitude":39.1}
                                """.formatted(code)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private String login(String username, String password) throws Exception {
        return read(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }

    private JsonNode publishAgreement(String master) throws Exception {
        MvcResult current = mockMvc.perform(get("/api/mp/agreements/current")).andReturn();
        if (current.getResponse().getStatus() == 200) {
            JsonNode data = objectMapper.readTree(current.getResponse().getContentAsString()).get("data");
            if (data != null && !data.isNull() && data.get("id") != null) {
                return data;
            }
        }
        mockMvc.perform(put("/api/admin/agreements/1")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"会员协议\",\"content\":\"购买前请阅读\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/agreements/1/publish").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk());
        return read(mockMvc.perform(get("/api/mp/agreements/current")).andExpect(status().isOk()).andReturn());
    }

    private String enroll(String phone) throws Exception {
        String sessionToken = read(mockMvc.perform(post("/api/mp/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"vendor-" + phone + "\"}"))
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
