package com.gym.self;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void storeAccountCannotReadAnotherStoreAndDisabledTokenStopsWorking() throws Exception {
        String master = login("admin", "admin123");
        String storeA = createStore(master, "A001", "A店");
        String storeB = createStore(master, "B001", "B店");
        String clerkId = createAccount(master, "clerk-a", storeA);
        String clerk = login("clerk-a", "pass1234");

        mockMvc.perform(get("/api/admin/stores").param("storeId", storeB).header("Authorization", "Bearer " + clerk))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(get("/api/admin/stores").header("Authorization", "Bearer " + clerk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(storeA));

        mockMvc.perform(post("/api/admin/accounts/" + clerkId + "/disable").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/stores").header("Authorization", "Bearer " + clerk))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void masterListsEveryAccountAndCanChangePassword() throws Exception {
        String master = login("admin", "admin123");
        String storeId = createStore(master, "C001", "C店");
        String clerkId = createAccount(master, "clerk-c", storeId);

        mockMvc.perform(get("/api/admin/accounts").header("Authorization", "Bearer " + master))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.username == 'admin')].role", hasItem("MASTER")))
                .andExpect(jsonPath("$.data[?(@.username == 'clerk-c')].storeName", hasItem("C店")));

        String clerk = login("clerk-c", "pass1234");
        mockMvc.perform(get("/api/admin/accounts").header("Authorization", "Bearer " + clerk))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/accounts/" + clerkId + "/password")
                        .header("Authorization", "Bearer " + master)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"newpass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.self").value(false));

        mockMvc.perform(get("/api/admin/stores").header("Authorization", "Bearer " + clerk))
                .andExpect(status().isUnauthorized());
        String clerkAgain = login("clerk-c", "newpass1");

        mockMvc.perform(delete("/api/admin/accounts/1").header("Authorization", "Bearer " + master))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/accounts/" + clerkId).header("Authorization", "Bearer " + clerkAgain))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/accounts/" + clerkId).header("Authorization", "Bearer " + master))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"clerk-c\",\"password\":\"newpass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }

    private String createStore(String token, String code, String name) throws Exception {
        String body = """
                {"code":"%s","name":"%s","province":"天津市","city":"天津市","address":"测试地址","longitude":117.2,"latitude":39.1}
                """.formatted(code, name);
        MvcResult result = mockMvc.perform(post("/api/admin/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String createAccount(String token, String username, String storeId) throws Exception {
        String body = """
                {"username":"%s","password":"pass1234","storeId":"%s"}
                """.formatted(username, storeId);
        MvcResult result = mockMvc.perform(post("/api/admin/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }
}
