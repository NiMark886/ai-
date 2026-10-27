package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Rejects structurally invalid AI-generated blueprints so the caller can fall
 * back to the deterministic mock generator instead of persisting bad data.
 */
@Component
public class InterviewBlueprintValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of("BASIC", "PROJECT", "SCENARIO", "AI");
    private static final int MIN_QUESTIONS = 5;
    private static final int MAX_QUESTIONS = 12;

    public void validate(InterviewBlueprint blueprint) {
        if (blueprint == null || blueprint.sections() == null || blueprint.sections().isEmpty()) {
            throw new IllegalArgumentException("Blueprint must contain at least one section");
        }

        int totalQuestions = 0;
        for (BlueprintSection section : blueprint.sections()) {
            if (section == null) {
                throw new IllegalArgumentException("Blueprint section cannot be null");
            }
            if (section.type() == null || !ALLOWED_TYPES.contains(section.type())) {
                throw new IllegalArgumentException("Unsupported section type: " + section.type());
            }
            if (section.questions() == null || section.questions().isEmpty()) {
                throw new IllegalArgumentException("Section must contain at least one question: " + section.type());
            }
            for (BlueprintQuestion question : section.questions()) {
                validateQuestion(question);
                totalQuestions++;
            }
        }

        if (totalQuestions < MIN_QUESTIONS || totalQuestions > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Blueprint question count out of range: " + totalQuestions);
        }
    }

    private void validateQuestion(BlueprintQuestion question) {
        if (question == null || question.text() == null || question.text().isBlank()) {
            throw new IllegalArgumentException("Question text cannot be blank");
        }
        if (question.difficulty() < 1 || question.difficulty() > 3) {
            throw new IllegalArgumentException("Question difficulty must be between 1 and 3");
        }
        validatePoints(question.referencePoints(), "referencePoints", question.text());
    }

    private void validatePoints(List<String> points, String field, String questionText) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty: " + questionText);
        }
        for (String point : points) {
            if (point == null || point.isBlank()) {
                throw new IllegalArgumentException(field + " contains a blank item: " + questionText);
            }
        }
    }
}
