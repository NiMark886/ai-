package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record InterviewReportView(
        Long id,
        Long sessionId,
        String jobTitle,
        String mode,
        int overallScore,
        Map<String, Integer> dimensions,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions,
        String status,
        LocalDateTime createdAt,
        List<QuestionReviewView> questionReviews
) {
}
