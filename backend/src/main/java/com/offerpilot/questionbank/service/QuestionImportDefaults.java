package com.offerpilot.questionbank.service;

/**
 * Values applied to extracted questions when the document or AI response does
 * not provide them.
 */
public record QuestionImportDefaults(
        String category,
        String level,
        Integer difficulty,
        String source
) {
}
