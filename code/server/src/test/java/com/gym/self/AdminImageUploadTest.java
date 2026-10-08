package com.gym.self;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminImageUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadReturnsAUrlTheMiniProgramCanRead() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("data").get("accessToken").asText();
        byte[] png = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", png);

        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/files/images")
                        .file(file)
                        .param("biz", "BANNER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").exists())
                .andReturn();
        String url = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("data").get("url").asText();
        String path = url.substring(url.indexOf("/uploads/"));
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));
    }
}
