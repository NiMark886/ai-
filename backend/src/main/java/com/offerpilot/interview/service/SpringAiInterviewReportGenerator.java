package com.offerpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.ai.service.AiCallExecutor;
import com.offerpilot.ai.service.ChatClientProvider;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiInterviewReportGenerator implements InterviewReportGenerator {

    private static final String SCENE = "REPORT";
    private static final String PROMPT_VERSION = "report-v1";

    private final ChatClientProvider chatClientProvider;
    private final ObjectMapper objectMapper;
    private final AiCallExecutor aiCallExecutor;
    private final MockInterviewReportGenerator fallback;

    public SpringAiInterviewReportGenerator(ChatClientProvider chatClientProvider,
                                            ObjectMapper objectMapper,
                                            AiCallExecutor aiCallExecutor,
                                            MockInterviewReportGenerator fallback) {
        this.chatClientProvider = chatClientProvider;
        this.objectMapper = objectMapper;
        this.aiCallExecutor = aiCallExecutor;
        this.fallback = fallback;
    }

    @Override
    public InterviewReportResult generate(InterviewSession session,
                                           List<InterviewQuestion> questions,
                                           List<InterviewMessage> messages,
                                           List<AnswerEvaluation> evaluations) {
        ChatClient chatClient = chatClientProvider.get();
        return aiCallExecutor.call(SCENE, PROMPT_VERSION,
                () -> {
                    String context;
                    try {
                        context = objectMapper.writeValueAsString(Map.of(
                                "mode", session.getMode(),
                                "questions", questions,
                                "messages", messages,
                                "evaluations", evaluations));
                    } catch (Exception ex) {
                        throw new IllegalStateException("AI report context serialization failed", ex);
                    }
                    return chatClient.prompt()
                            .system("""
                                    你是技术面试报告生成器。根据面试模式、题目、候选人回答和单题评估，
                                    输出五维评分报告。只输出 JSON，不要 markdown。JSON 字段：
                                    dimensions（accuracy/depth/project/expression/match，0-100 整数）、
                                    overallScore（0-100）、summary（中文总结）、
                                    strengths（字符串数组）、weaknesses（字符串数组）、suggestions（字符串数组）。
                                    """)
                            .user("面试上下文：\n" + context)
                            .call()
                            .chatResponse();
                },
                response -> objectMapper.readValue(
                        cleanJson(response.getResult().getOutput().getText()),
                        InterviewReportResult.class),
                () -> fallback.generate(session, questions, messages, evaluations));
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}
