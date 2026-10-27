package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.CreateInterviewRequest;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.QuestionView;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.mapper.ResumeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class InterviewService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final JobTemplateMapper jobTemplateMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewBlueprintGenerator blueprintGenerator;
    private final ObjectMapper objectMapper;

    public InterviewService(InterviewSessionMapper sessionMapper,
                            InterviewQuestionMapper questionMapper,
                            JobTemplateMapper jobTemplateMapper,
                            ResumeMapper resumeMapper,
                            InterviewBlueprintGenerator blueprintGenerator,
                            ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.jobTemplateMapper = jobTemplateMapper;
        this.resumeMapper = resumeMapper;
        this.blueprintGenerator = blueprintGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionView create(Long userId, CreateInterviewRequest request) {
        JobTemplate job = jobTemplateMapper.selectById(request.jobTemplateId());
        if (job == null || !"ACTIVE".equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "岗位模板不存在");
        }

        Resume resume = resumeMapper.selectById(request.resumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
        }

        ResumeProfile profile = readProfile(resume);
        InterviewBlueprint blueprint = blueprintGenerator.generate(new BlueprintRequest(
                job, profile, request.difficulty(), request.durationMinutes(), request.persona()));

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setJobTemplateId(job.getId());
        session.setResumeId(resume.getId());
        session.setDifficulty(request.difficulty());
        session.setDurationMinutes(request.durationMinutes());
        session.setPersona(request.persona());
        session.setStatus("READY");
        session.setBlueprintJson(writeJson(blueprint));
        sessionMapper.insert(session);

        List<QuestionView> questions = saveQuestions(session.getId(), blueprint);
        return toView(session, job.getTitle(), questions);
    }

    public InterviewSessionView get(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
        List<QuestionView> questions = questionMapper.selectList(
                        new LambdaQueryWrapper<InterviewQuestion>()
                                .eq(InterviewQuestion::getSessionId, sessionId)
                                .orderByAsc(InterviewQuestion::getSeq))
                .stream()
                .map(this::toQuestionView)
                .toList();
        return toView(session, job == null ? "未知岗位" : job.getTitle(), questions);
    }

    public List<InterviewSessionView> list(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt))
                .stream()
                .map(session -> {
                    JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
                    return toView(session, job == null ? "未知岗位" : job.getTitle(), List.of());
                })
                .toList();
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试不存在");
        }
        return session;
    }

    private List<QuestionView> saveQuestions(Long sessionId, InterviewBlueprint blueprint) {
        List<QuestionView> result = new ArrayList<>();
        int seq = 1;
        for (BlueprintSection section : blueprint.sections()) {
            for (BlueprintQuestion question : section.questions()) {
                InterviewQuestion entity = new InterviewQuestion();
                entity.setSessionId(sessionId);
                entity.setSeq(seq++);
                entity.setType(section.type());
                entity.setDifficulty(question.difficulty());
                entity.setContent(question.text());
                entity.setKnowledgeTags(writeJson(question.knowledgeTags()));
                entity.setReferencePoints(writeJson(question.referencePoints()));
                entity.setFollowUpHints(writeJson(question.followUpHints()));
                entity.setStatus("PENDING");
                questionMapper.insert(entity);
                result.add(toQuestionView(entity));
            }
        }
        return result;
    }

    private QuestionView toQuestionView(InterviewQuestion question) {
        return new QuestionView(
                question.getId(),
                question.getSeq(),
                question.getType(),
                question.getDifficulty(),
                question.getContent(),
                readList(question.getKnowledgeTags()),
                readList(question.getReferencePoints()),
                question.getStatus(),
                question.getFollowUpCount(),
                question.getHintCount());
    }

    private InterviewSessionView toView(InterviewSession session, String jobTitle,
                                        List<QuestionView> questions) {
        return new InterviewSessionView(
                session.getId(),
                session.getJobTemplateId(),
                jobTitle,
                session.getResumeId(),
                session.getDifficulty(),
                session.getDurationMinutes(),
                session.getPersona(),
                session.getStatus(),
                session.getCurrentQuestionId(),
                session.getCreatedAt(),
                questions);
    }

    private ResumeProfile readProfile(Resume resume) {
        if (resume.getParsedJson() == null) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
        try {
            return objectMapper.readValue(resume.getParsedJson(), ResumeProfile.class);
        } catch (Exception ex) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "面试蓝图序列化失败");
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
