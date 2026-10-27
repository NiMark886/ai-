package com.offerpilot.interview;

import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.MockBlueprintGenerator;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockBlueprintGeneratorTest {

    private final MockBlueprintGenerator generator = new MockBlueprintGenerator();

    @Test
    void generatesEightQuestionsForThirtyMinutes() {
        JobTemplate job = new JobTemplate();
        job.setTitle("Java 后端开发（校招）");
        job.setCategory("Java 后端");
        job.setConfigJson("{\"basic\":30,\"project\":35,\"scenario\":20,\"ai\":15}");

        ResumeProfile profile = new ResumeProfile(
                "张三", "zhangsan@example.com", "13800138000", 3,
                List.of("Java", "Spring Boot", "MySQL", "Redis"),
                List.of(new ProjectExperience("中州养老", "负责设备数据采集与报警规则")),
                "某大学本科", "3 年 Java 后端经验");

        InterviewBlueprint blueprint = generator.generate(new BlueprintRequest(
                job, profile, "STANDARD", 30, "STANDARD"));

        long count = blueprint.sections().stream()
                .mapToLong(section -> section.questions().size())
                .sum();
        assertThat(count).isEqualTo(8);
        assertThat(blueprint.sections()).isNotEmpty();
        assertThat(blueprint.sections().get(0).questions())
                .allSatisfy(question -> {
                    assertThat(question.text()).isNotBlank();
                    assertThat(question.referencePoints()).isNotEmpty();
                });
    }
}
