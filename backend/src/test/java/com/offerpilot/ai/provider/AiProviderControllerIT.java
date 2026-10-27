package com.offerpilot.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class AiProviderControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void managesProvidersPerUserAndMasksKeys() throws Exception {
        String token = registerAndToken();
        String otherToken = registerAndToken();

        mockMvc.perform(get("/api/ai/providers").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/ai/providers/presets").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        mockMvc.perform(get("/api/ai/providers/status").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiEnabled").value(false))
                .andExpect(jsonPath("$.data.defaultModel").value("deepseek-chat"));

        MvcResult created = mockMvc.perform(post("/api/ai/providers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "DeepSeek",
                                "providerType", "DEEPSEEK",
                                "apiKey", "sk-1234567890abcd",
                                "temperature", 0.4,
                                "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.apiKeyHint").value("sk-****abcd"))
                .andReturn();
        String createdBody = created.getResponse().getContentAsString();
        assertThat(createdBody).doesNotContain("sk-1234567890abcd");
        long deepSeekId = objectMapper.readTree(createdBody).get("data").get("id").asLong();

        MvcResult miMo = mockMvc.perform(post("/api/ai/providers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "MiMo",
                                "providerType", "MIMO",
                                "apiKey", "tp-abcdefghijklmnop",
                                "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.baseUrl").value("https://api.xiaomimimo.com/v1"))
                .andExpect(jsonPath("$.data.model").value("mimo-v2.5"))
                .andReturn();
        long miMoId = objectMapper.readTree(miMo.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/ai/providers/" + miMoId + "/activate")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(put("/api/ai/providers/" + deepSeekId)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "DeepSeek",
                                "providerType", "DEEPSEEK",
                                "baseUrl", "https://api.deepseek.com",
                                "model", "deepseek-chat",
                                "apiKey", "",
                                "temperature", 0.4,
                                "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKeyHint").value("sk-****abcd"));

        mockMvc.perform(get("/api/ai/providers").header(AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(put("/api/ai/providers/" + deepSeekId)
                        .header(AUTHORIZATION, "Bearer " + otherToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Hijack",
                                "providerType", "CUSTOM",
                                "baseUrl", "https://example.com/v1",
                                "model", "custom-model",
                                "apiKey", "sk-hijack",
                                "enabled", true))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/ai/providers/" + miMoId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult remaining = mockMvc.perform(get("/api/ai/providers")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        JsonNode provider = objectMapper.readTree(remaining.getResponse().getContentAsString())
                .get("data").get(0);
        assertThat(provider.get("active").asBoolean()).isTrue();
        assertThat(provider.get("name").asText()).isEqualTo("DeepSeek");
    }

    private String registerAndToken() throws Exception {
        String email = "provider-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Provider Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }
}
