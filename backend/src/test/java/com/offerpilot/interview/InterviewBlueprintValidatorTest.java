package com.offerpilot.interview;

import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.InterviewBlueprintValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewBlueprintValidatorTest {

    private final InterviewBlueprintValidator validator = new InterviewBlueprintValidator();

    @Test
    void acceptsValidBlueprint() {
        assertThatCode(() -> validator.validate(validBlueprint())).doesNotThrowAnyException();
    }

    @Test
    void rejectsQuestionWithoutReferencePoints() {
        BlueprintQuestion question = new BlueprintQuestion("问题", 2, List.of("Java"), List.of(), List.of());
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BASIC", 100,
                        List.of(question, question, question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referencePoints");
    }

    @Test
    void rejectsUnsupportedSectionType() {
        BlueprintQuestion question = new BlueprintQuestion("问题", 2, List.of("Java"),
                List.of("要点"), List.of());
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("UNKNOWN", 100,
                        List.of(question, question, question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported section type");
    }

    @Test
    void rejectsTooFewQuestions() {
        BlueprintQuestion question = new BlueprintQuestion("问题", 2, List.of("Java"),
                List.of("要点"), List.of());
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BASIC", 100,
                        List.of(question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question count");
    }

    @Test
    void rejectsEmptySections() {
        InterviewBlueprint blueprint = new InterviewBlueprint("opening", List.of(), "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one section");
    }

    private InterviewBlueprint validBlueprint() {
        BlueprintQuestion question = new BlueprintQuestion("请介绍 HashMap", 2, List.of("Java"),
                List.of("数组", "链表", "红黑树"), List.of("扩容过程"));
        return new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BASIC", 50,
                                List.of(question, question, question)),
                        new BlueprintSection("PROJECT", 50,
                                List.of(question, question))),
                "closing");
    }
}
