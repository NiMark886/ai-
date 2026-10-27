package com.offerpilot.interview.web.dto;

public record AnswerStreamContext(
        Long sessionId,
        Long questionId,
        Long candidateMessageId,
        Long interviewerMessageId,
        String replyType,
        Long nextQuestionId,
        boolean practice,
        String promptText,
        String promptType,
        String answer
) {
}
