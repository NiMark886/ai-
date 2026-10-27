package com.offerpilot.questionbank.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuestionBankAdminRequest(
        @NotBlank String category,
        @NotBlank String level,
        @NotBlank String tags,
        @NotNull @Min(1) @Max(3) Integer difficulty,
        @NotBlank String question,
        @NotBlank String referenceAnswer,
        String source,
        String status
) {
}
