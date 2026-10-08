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
    void faceEnrollIssuesMemberNoAndSecondLoginSkipsCapture() throws Exception {
        String session = session("dev-user-a");
        mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + session))
                .andExpect(status().isUnauthorized());

        String face = phone(session, "13800001111");
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
        MvcResult enrolled = mockMvc.perform(post("/api/mp/face")
                        .header("Authorization", "Bearer " + face)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + retryKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("ACCESS"))
                .andExpect(jsonPath("$.data.memberNo").value("100000001"))
                .andReturn();
        String access = objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("data").get("accessToken").asText();

        mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberNo").value("100000001"))
                .andExpect(jsonPath("$.data.registerStatus").value("ACTIVE"));

        String again = phone(session("dev-user-b"), "13800001111");
        mockMvc.perform(get("/api/mp/me").header("Authorization", "Bearer " + again))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberNo").value("100000001"));
        assertNotEquals(face, again);
    }

    private String session(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mp/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("sessionToken").asText();
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
