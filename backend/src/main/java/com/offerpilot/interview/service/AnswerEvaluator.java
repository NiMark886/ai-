package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;

public interface AnswerEvaluator {

    AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                    String promptType, String answer);
}
