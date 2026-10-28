package com.offerpilot.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.job.web.dto.JobTemplateView;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.service.InterviewTrackService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class JobTemplateService {

    private final JobTemplateMapper jobTemplateMapper;
    private final InterviewTrackService trackService;

    public JobTemplateService(JobTemplateMapper jobTemplateMapper,
                              InterviewTrackService trackService) {
        this.jobTemplateMapper = jobTemplateMapper;
        this.trackService = trackService;
    }

    public List<JobTemplateView> listActive() {
        Map<Long, InterviewTrack> activeTracks = trackService.listActive().stream()
                .collect(Collectors.toMap(InterviewTrack::getId, track -> track));

        return jobTemplateMapper.selectList(new LambdaQueryWrapper<JobTemplate>()
                        .eq(JobTemplate::getStatus, "ACTIVE")
                        .orderByAsc(JobTemplate::getSortOrder))
                .stream()
                .filter(job -> job.getTrackId() == null || activeTracks.containsKey(job.getTrackId()))
                .map(job -> {
                    InterviewTrack track = job.getTrackId() == null ? null : activeTracks.get(job.getTrackId());
                    long count = track == null ? 0 : trackService.countQuestions(track.getId());
                    return JobTemplateView.from(job, track, count);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
