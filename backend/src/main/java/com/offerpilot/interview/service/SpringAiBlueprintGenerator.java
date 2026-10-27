package com.offerpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiBlueprintGenerator implements InterviewBlueprintGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiBlueprintGenerator.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final MockBlueprintGenerator fallback;

    public SpringAiBlueprintGenerator(ChatClient.Builder builder,
                                      ObjectMapper objectMapper,
                                      MockBlueprintGenerator fallback) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public InterviewBlueprint generate(BlueprintRequest request) {
        try {
            int count = switch (request.durationMinutes()) {
                case 20 -> 6;
                case 45 -> 10;
                default -> 8;
            };
            String content = chatClient.prompt()
                    .system("""
                            你是一名资深技术面试官。根据岗位 JD 和候选人简历生成一场模拟面试蓝图。
                            只输出 JSON，不要 markdown 代码块。JSON 结构：
                            {"opening":"...","sections":[{"type":"BASIC","weight":30,"questions":[{"text":"...","difficulty":2,"knowledgeTags":["..."],"referencePoints":["..."],"followUpHints":["..."]}]}],"closing":"..."}
                            type 只能是 BASIC/PROJECT/SCENARIO/AI。所有题目必须有 referencePoints。
                            """)
                    .user("""
                            岗位：%s
                            JD：%s
                            候选人简历：%s
                            难度：%s
                            时长：%d 分钟
                            面试官风格：%s
                            题目总数：%d
                            参考题库：%s
                            """.formatted(
                            request.jobTemplate().getTitle(),
                            request.jobTemplate().getJdText(),
                            request.resumeProfile(),
                            request.difficulty(),
                            request.durationMinutes(),
                            request.persona(),
                            count,
                            request.retrievedQuestions()))
                    .call()
                    .content();

            InterviewBlueprint blueprint = objectMapper.readValue(cleanJson(content), InterviewBlueprint.class);
            if (blueprint.sections() == null || blueprint.sections().isEmpty()) {
                throw new IllegalStateException("AI blueprint is empty");
            }
            return blueprint;
        } catch (Exception ex) {
            log.warn("AI blueprint generation failed, falling back to mock generator", ex);
            return fallback.generate(request);
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
