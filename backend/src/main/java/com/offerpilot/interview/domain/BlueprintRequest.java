package com.offerpilot.interview.domain;

import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.resume.domain.ResumeProfile;

import java.util.List;

public record BlueprintRequest(
        JobTemplate jobTemplate,
        ResumeProfile resumeProfile,
        String difficulty,
        int durationMinutes,
        String persona,
        List<QuestionBankItem> retrievedQuestions
) {
}
