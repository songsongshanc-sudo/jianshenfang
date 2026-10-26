package com.gym.self;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MpRegisterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void memberNoIssuedWhenProfileCompleted() throws Exception {
        String entered = enter("dev-user-a");
        mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + entered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        String face = phone(entered, "13800001111");
        mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + face))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        String objectKey = presign(face);
        mockMvc.perform(post("/api/mp/files/upload")
                        .header("Authorization", "Bearer " + face)
                        .header("X-Object-Key", objectKey)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[8]))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/mp/face")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未检测到正脸，请重新拍摄"));

        String retryKey = presign(face);
        mockMvc.perform(post("/api/mp/files/upload")
                        .header("Authorization", "Bearer " + face)
                        .header("X-Object-Key", retryKey)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[64]))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/mp/face")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + retryKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("REGISTER"))
                .andExpect(jsonPath("$.data.memberNo").value(org.hamcrest.Matchers.nullValue()));

        MvcResult completed = mockMvc.perform(post("/api/mp/auth/complete")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试会员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("ACCESS"))
                .andExpect(jsonPath("$.data.registerStatus").value("ACTIVE"))
                .andReturn();
        String memberNo = data(completed).get("memberNo").asText();
        assertTrue(memberNo.matches("\\d{9}"));
        String access = data(completed).get("accessToken").asText();

        mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberNo").value(memberNo))
                .andExpect(jsonPath("$.data.registerStatus").value("ACTIVE"));

        String again = enter("dev-user-a");
        mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + again))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberNo").value(memberNo));
        assertNotEquals(face, again);

        mockMvc.perform(post("/api/mp/auth/phone")
                        .header("Authorization", "Bearer " + enter("dev-user-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneCode\":\"13800001111\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该手机号已绑定其他微信"));
    }

    private String enter(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mp/auth/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("accessToken").asText();
    }

    private String phone(String sessionToken, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mp/auth/phone")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneCode\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("accessToken").asText();
    }

    private String presign(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mp/files/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biz\":\"FACE\",\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("objectKey").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
