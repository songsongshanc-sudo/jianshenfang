package com.gym.self;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.card.domain.CardProduct;
import com.gym.self.modules.card.domain.CardProductMapper;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardCatalogTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TimeProvider timeProvider;
    @Autowired
    private CardProductMapper cardProductMapper;
    @Autowired
    private MembershipMapper membershipMapper;
    @Autowired
    private GymUserMapper userMapper;
    @Autowired
    private Snowflake snowflake;

    @Test
    void guestSeesLockedCardsAndMemberUnlocksAfterEnoughDays() throws Exception {
        String master = login("admin", "admin123");
        String storeId = createStore(master);
        String clerk = loginAfterCreate(master, "clerk-card", storeId);
        mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + clerk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson(storeId, "门店自建卡", 100, 1, null, "NONE", null, 0, "ON")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + clerk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson("1", "越权卡", 100, 1, null, "NONE", null, 0, "ON")))
                .andExpect(status().isForbidden());

        createCard(master, storeId, "周卡", 3800, 7, null, "NONE", null, 1, "ON");
        createCard(master, storeId, "限量年卡", 60900, 365, 14, "NONE", null, 0, "ON");
        String soldOutId = createCard(master, storeId, "售罄卡", 100, 1, 1, "NONE", null, 0, "ON");
        CardProduct soldOut = cardProductMapper.selectById(Long.parseLong(soldOutId));
        soldOut.setStockSold(1);
        cardProductMapper.updateById(soldOut);
        createCard(master, storeId, "大会员", 9900, 31, null, "CUMULATIVE_DAYS", 90, 1, "ON");
        createCard(master, storeId, "连续月卡", 5990, 31, null, "CONSECUTIVE_DAYS", 179, 0, "ON");
        createCard(master, storeId, "下架卡", 100, 1, null, "NONE", null, 1, "OFF");
        String otherStore = createStore(master, "CARD2");
        String foreignCard = createCard(master, otherStore, "外店卡", 100, 1, null, "NONE", null, 0, "ON");
        mockMvc.perform(put("/api/admin/cards/" + foreignCard)
                        .header("Authorization", "Bearer " + clerk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson(storeId, "外店卡", 100, 1, null, "NONE", null, 0, "ON")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/mp/cards").param("storeId", storeId).param("placement", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='周卡')].purchasable", contains(true)))
                .andExpect(jsonPath("$.data[?(@.name=='限量年卡')].remaining", contains(14)))
                .andExpect(jsonPath("$.data[?(@.name=='售罄卡')].purchasable", contains(false)))
                .andExpect(jsonPath("$.data[?(@.name=='售罄卡')].lockText", contains("已售罄")))
                .andExpect(jsonPath("$.data[?(@.name=='大会员')].purchasable", contains(false)))
                .andExpect(jsonPath("$.data[?(@.name=='大会员')].lockText", contains("累计满 90 天后激活此卡")))
                .andExpect(jsonPath("$.data[?(@.name=='连续月卡')].lockText", contains("连续满 179 天后激活此卡")))
                .andExpect(jsonPath("$.data[?(@.name=='下架卡')]", hasSize(0)));

        mockMvc.perform(get("/api/mp/cards").param("storeId", storeId).param("placement", "HOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        timeProvider.use(Clock.fixed(LocalDateTime.of(2026, 10, 8, 12, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), TimeProvider.ZONE));
        try {
            String access = enroll("13900002222");
            GymUser user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GymUser>()
                    .eq(GymUser::getPhone, "13900002222"));
            Membership membership = new Membership();
            membership.setId(snowflake.next());
            membership.setUserId(user.getId());
            membership.setStoreId(Long.parseLong(storeId));
            membership.setOrderId(snowflake.next());
            membership.setCardProductId(Long.parseLong(soldOutId));
            membership.setSource("PAY");
            membership.setStartAt(LocalDateTime.of(2026, 7, 11, 0, 0));
            membership.setEndAt(LocalDateTime.of(2026, 10, 9, 0, 0));
            membership.setStatus("ACTIVE");
            membership.setCrossStore(0);
            membership.setCreatedAt(LocalDateTime.of(2026, 7, 11, 0, 0));
            membershipMapper.insert(membership);

            mockMvc.perform(get("/api/mp/cards").param("storeId", storeId).param("placement", "ALL")
                            .header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.name=='大会员')].purchasable", contains(true)))
                    .andExpect(jsonPath("$.data[?(@.name=='连续月卡')].purchasable", contains(false)));
            mockMvc.perform(get("/api/mp/me").param("storeId", storeId).header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.cumulativeDays").value(90))
                    .andExpect(jsonPath("$.data.consecutiveDays").value(90))
                    .andExpect(jsonPath("$.data.consecutiveRemain").value(89))
                    .andExpect(jsonPath("$.data.storeMember").value(true));
        } finally {
            timeProvider.reset();
        }
    }

    private String createCard(String token, String storeId, String name, int priceFen, int days, Integer stock,
                              String unlock, Integer unlockDays, int home, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson(storeId, name, priceFen, days, stock, unlock, unlockDays, home, status)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private static String cardJson(String storeId, String name, int priceFen, int days, Integer stock, String unlock,
                                   Integer unlockDays, int home, String status) {
        return """
                {"storeId":"%s","name":"%s","priceFen":%d,"validDays":%d,"durationMode":"HOURS_24","stockTotal":%s,"unlockType":"%s","unlockDays":%s,"crossStore":0,"homeVisible":%d,"sortNo":1,"status":"%s"}
                """.formatted(storeId, name, priceFen, days, stock == null ? "null" : stock, unlock,
                unlockDays == null ? "null" : unlockDays, home, status);
    }

    private String enroll(String phone) throws Exception {
        MvcResult session = mockMvc.perform(post("/api/mp/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"card-" + phone + "\"}"))
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

    private String createStore(String token) throws Exception {
        return createStore(token, "CARD1");
    }

    private String createStore(String token, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"卡种店","province":"天津市","city":"天津市","address":"测试路","longitude":117.2,"latitude":39.1}
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
}
