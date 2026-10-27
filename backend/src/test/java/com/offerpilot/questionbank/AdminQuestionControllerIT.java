package com.offerpilot.questionbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.user.web.dto.LoginRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminQuestionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCrudQuestionsAndUserCannot() throws Exception {
        String adminToken = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Java 基础",
                                "level", "校招",
                                "tags", "Java,测试",
                                "difficulty", 2,
                                "question", "测试题目：HashMap 的负载因子是多少？",
                                "referenceAnswer", "默认 0.75。",
                                "source", "测试",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .param("keyword", "负载因子"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(put("/api/admin/questions/" + id)
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Java 基础",
                                "level", "校招",
                                "tags", "Java,测试",
                                "difficulty", 2,
                                "question", "更新后的题目：HashMap 负载因子？",
                                "referenceAnswer", "默认 0.75。",
                                "source", "测试",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").value("更新后的题目：HashMap 负载因子？"));

        mockMvc.perform(delete("/api/admin/questions/" + id)
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        String userToken = userToken();
        mockMvc.perform(get("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@offerpilot.local", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String userToken() throws Exception {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Normal User"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }
}
