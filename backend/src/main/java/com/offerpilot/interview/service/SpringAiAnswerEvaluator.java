package com.offerpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiAnswerEvaluator implements AnswerEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAnswerEvaluator.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final MockAnswerEvaluator fallback;

    public SpringAiAnswerEvaluator(ChatClient.Builder builder,
                                   ObjectMapper objectMapper,
                                   MockAnswerEvaluator fallback) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                           String promptType, String answer) {
        try {
            String content = chatClient.prompt()
                    .system("""
                            你是技术面试评分器。根据当前评分对象、原题、参考要点和候选人回答判断回答正确性。
                            只输出 JSON，不要 markdown。JSON 字段：
                            correctness（CORRECT/PARTIAL/INSUFFICIENT/INCORRECT）、
                            score（1-5）、comment（一句中文点评，不要重复列 missingPoints）、
                            missingPoints（字符串数组）、confidence（0-1）。
                            如果当前评分对象类型是 FOLLOW_UP，只评价候选人是否针对该追问作答、
                            是否有具体展开，不要拿原题的全部参考要点要求候选人。
                            """)
                    .user("""
                            当前评分对象类型：%s
                            当前评分对象：%s
                            原题：%s
                            参考要点：%s
                            候选人回答：%s
                            """.formatted(
                            promptType == null ? "QUESTION" : promptType,
                            promptText == null ? "无" : promptText,
                            question.getContent(),
                            question.getReferencePoints(),
                            answer))
                    .call()
                    .content();
            return objectMapper.readValue(cleanJson(content), AnswerEvaluationResult.class);
        } catch (Exception ex) {
            log.warn("AI answer evaluation failed, falling back to mock", ex);
            return fallback.evaluate(question, promptText, promptType, answer);
        }
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}
