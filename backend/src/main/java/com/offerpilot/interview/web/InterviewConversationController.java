package com.offerpilot.interview.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewConversationService;
import com.offerpilot.interview.web.dto.AnswerEvaluationView;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerResult;
import com.offerpilot.interview.web.dto.HintResult;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.MessageView;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews/{id}")
public class InterviewConversationController {

    private final InterviewConversationService conversationService;
    private final ThreadPoolTaskExecutor sseExecutor;
    private final ObjectMapper objectMapper;

    public InterviewConversationController(InterviewConversationService conversationService,
                                           @Qualifier("interviewSseExecutor") ThreadPoolTaskExecutor sseExecutor,
                                           ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.sseExecutor = sseExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/start")
    public ApiResponse<InterviewSessionView> start(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(conversationService.start(userId, id));
    }

    @GetMapping("/messages")
    public ApiResponse<List<MessageView>> messages(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(conversationService.messages(userId, id));
    }

    @GetMapping("/evaluations")
    public ApiResponse<List<AnswerEvaluationView>> evaluations(@AuthenticationPrincipal Long userId,
                                                               @PathVariable Long id) {
        return ApiResponse.ok(conversationService.evaluations(userId, id));
    }

    @PostMapping(value = "/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@AuthenticationPrincipal Long userId,
                             @PathVariable Long id,
                             @Valid @RequestBody AnswerRequest request) {
        AnswerResult result = conversationService.answer(userId, id, request);
        SseEmitter emitter = new SseEmitter(60_000L);
        sseExecutor.execute(() -> stream(emitter, result));
        return emitter;
    }

    @PostMapping("/hint")
    public ApiResponse<HintResult> hint(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.hint(userId, id));
    }

    @PostMapping("/skip")
    public ApiResponse<InterviewSessionView> skip(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.skip(userId, id));
    }

    @PostMapping("/finish")
    public ApiResponse<InterviewSessionView> finish(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.finish(userId, id));
    }

    private void stream(SseEmitter emitter, AnswerResult result) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("candidateMessageId", result.candidateMessageId());
            meta.put("interviewerMessageId", result.interviewerMessageId());
            meta.put("questionId", result.questionId());
            meta.put("replyType", result.replyType());
            emitter.send(SseEmitter.event().name("meta").data(toJson(meta)));

            for (String chunk : chunks(result.replyText())) {
                emitter.send(SseEmitter.event().name("delta").data(toJson(Map.of("text", chunk))));
                Thread.sleep(25);
            }

            if (result.evaluation() != null) {
                emitter.send(SseEmitter.event().name("evaluation").data(toJson(result.evaluation())));
            }

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionStatus", result.sessionStatus());
            done.put("currentQuestionId", result.currentQuestionId());
            done.put("completed", result.completed());
            emitter.send(SseEmitter.event().name("done").data(toJson(done)));
            emitter.complete();
        } catch (Exception ex) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(toJson(Map.of("message", "面试官回复失败"))));
            } catch (IOException ignored) {
                // client already disconnected
            }
            emitter.completeWithError(ex);
        }
    }

    private List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        for (int i = 0; i < text.length(); i += 24) {
            chunks.add(text.substring(i, Math.min(text.length(), i + 24)));
        }
        return chunks;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
