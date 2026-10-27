package com.offerpilot.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.TestFiles;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewReportControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatesReportHistoryAndStats() throws Exception {
        String token = registerAndToken();
        long resumeId = uploadResume(token);
        long sessionId = createInterview(token, resumeId);

        mockMvc.perform(post("/api/interviews/" + sessionId + "/start")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult sessionResult = mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long questionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("data").get("currentQuestionId").asLong();

        MvcResult sse = mockMvc.perform(post("/api/interviews/" + sessionId + "/answers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", questionId,
                                "content", "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍，会重新分布元素。我会结合一次线上扩容导致的性能抖动，说明如何通过预设容量和监控 GC 来优化。"))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(sse)).andExpect(status().isOk());

        mockMvc.perform(post("/api/interviews/" + sessionId + "/finish")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(post("/api/interviews/" + sessionId + "/report")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").isNumber())
                .andExpect(jsonPath("$.data.dimensions.accuracy").isNumber())
                .andExpect(jsonPath("$.data.dimensions.depth").isNumber())
                .andExpect(jsonPath("$.data.dimensions.project").isNumber())
                .andExpect(jsonPath("$.data.dimensions.expression").isNumber())
                .andExpect(jsonPath("$.data.dimensions.match").isNumber())
                .andExpect(jsonPath("$.data.questionReviews").isArray());

        mockMvc.perform(get("/api/interviews/" + sessionId + "/report")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").isNumber());

        mockMvc.perform(get("/api/interviews/history")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hasReport").value(true));

        mockMvc.perform(get("/api/stats/overview")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInterviews").value(1))
                .andExpect(jsonPath("$.data.completedInterviews").value(1))
                .andExpect(jsonPath("$.data.averageScore").isNumber())
                .andExpect(jsonPath("$.data.dimensionAverages.accuracy").isNumber());
    }

    private String registerAndToken() throws Exception {
        String email = "report-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Report Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private long uploadResume(String token) throws Exception {
        byte[] docx = TestFiles.docx("""
                张三
                邮箱：zhangsan@example.com
                3 年 Java 后端开发经验
                技能：Java, Spring Boot, MySQL, Redis
                项目：中州养老智能物联网平台
                负责设备数据采集与报警规则。
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        MvcResult result = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long createInterview(String token, long resumeId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobTemplateId", 1,
                                "resumeId", resumeId,
                                "difficulty", "STANDARD",
                                "durationMinutes", 30,
                                "persona", "STANDARD",
                                "mode", "INTERVIEW"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
