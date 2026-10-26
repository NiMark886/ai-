package com.offerpilot.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.job.web.dto.JobTemplateView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobTemplateService {

    private final JobTemplateMapper jobTemplateMapper;

    public JobTemplateService(JobTemplateMapper jobTemplateMapper) {
        this.jobTemplateMapper = jobTemplateMapper;
    }

    public List<JobTemplateView> listActive() {
        return jobTemplateMapper.selectList(new LambdaQueryWrapper<JobTemplate>()
                        .eq(JobTemplate::getStatus, "ACTIVE")
                        .orderByAsc(JobTemplate::getSortOrder))
                .stream()
                .map(JobTemplateView::from)
                .toList();
    }
}
