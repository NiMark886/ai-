package com.offerpilot.job.web.dto;

import com.offerpilot.job.domain.JobTemplate;

public record JobTemplateView(Long id, String title, String level, String category, String jdText) {

    public static JobTemplateView from(JobTemplate jobTemplate) {
        return new JobTemplateView(
                jobTemplate.getId(),
                jobTemplate.getTitle(),
                jobTemplate.getLevel(),
                jobTemplate.getCategory(),
                jobTemplate.getJdText());
    }
}
