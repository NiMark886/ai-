package com.offerpilot.interview.domain;

import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.ResumeProfile;

public record BlueprintRequest(
        JobTemplate jobTemplate,
        ResumeProfile resumeProfile,
        String difficulty,
        int durationMinutes,
        String persona
) {
}
