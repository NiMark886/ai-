package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewReport;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.AnswerEvaluationMapper;
import com.offerpilot.interview.mapper.InterviewMessageMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewReportMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.interview.web.dto.InterviewHistoryItem;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import com.offerpilot.interview.web.dto.InterviewReportView;
import com.offerpilot.interview.web.dto.QuestionReviewView;
import com.offerpilot.interview.web.dto.ScorePoint;
import com.offerpilot.interview.web.dto.StatsOverview;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewReportService {

    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final JobTemplateMapper jobTemplateMapper;
    private final AnswerEvaluator answerEvaluator;
    private final InterviewReportGenerator reportGenerator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public InterviewReportService(InterviewReportMapper reportMapper,
                                  InterviewSessionMapper sessionMapper,
                                  InterviewQuestionMapper questionMapper,
                                  InterviewMessageMapper messageMapper,
                                  AnswerEvaluationMapper evaluationMapper,
                                  JobTemplateMapper jobTemplateMapper,
                                  AnswerEvaluator answerEvaluator,
                                  InterviewReportGenerator reportGenerator,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate) {
        this.reportMapper = reportMapper;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.evaluationMapper = evaluationMapper;
        this.jobTemplateMapper = jobTemplateMapper;
        this.answerEvaluator = answerEvaluator;
        this.reportGenerator = reportGenerator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public InterviewReportView generate(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"COMPLETED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "面试尚未结束");
        }

        List<InterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getSeq));
        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId));
        List<AnswerEvaluation> evaluations = new ArrayList<>(evaluationMapper.selectList(
                new LambdaQueryWrapper<AnswerEvaluation>()
                        .eq(AnswerEvaluation::getSessionId, sessionId)
                        .orderByAsc(AnswerEvaluation::getId)));

        ensureEvaluations(session, questions, messages, evaluations);

        InterviewReport existing = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, sessionId));
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return buildView(existing, session, questions, messages, evaluations);
        }

        InterviewReportResult result = reportGenerator.generate(session, questions, messages, evaluations);
        InterviewReport report = existing == null ? new InterviewReport() : existing;
        if (existing == null) {
            report.setSessionId(sessionId);
            report.setUserId(userId);
        }
        report.setOverallScore(result.overallScore());
        report.setDimensionsJson(writeJson(result.dimensions()));
        report.setSummary(result.summary());
        report.setStrengthsJson(writeJson(result.strengths()));
        report.setWeaknessesJson(writeJson(result.weaknesses()));
        report.setSuggestionsJson(writeJson(result.suggestions()));
        report.setStatus("SUCCESS");
        transactionTemplate.executeWithoutResult(status -> {
            if (existing == null) {
                reportMapper.insert(report);
            } else {
                reportMapper.updateById(report);
            }
            session.setTotalScore(result.overallScore());
            sessionMapper.updateById(session);
        });

        return buildView(report, session, questions, messages, evaluations);
    }

    public InterviewReportView get(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        InterviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, sessionId));
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告尚未生成");
        }
        List<InterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getSeq));
        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId));
        List<AnswerEvaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<AnswerEvaluation>()
                        .eq(AnswerEvaluation::getSessionId, sessionId)
                        .orderByAsc(AnswerEvaluation::getId));
        return buildView(report, session, questions, messages, evaluations);
    }

    public List<InterviewHistoryItem> history(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt));
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<Long> jobIds = sessions.stream()
                .map(InterviewSession::getJobTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> jobTitles = jobIds.isEmpty() ? Map.of()
                : jobTemplateMapper.selectBatchIds(jobIds).stream()
                        .collect(Collectors.toMap(JobTemplate::getId, JobTemplate::getTitle, (first, second) -> first));
        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        Map<Long, InterviewReport> reports = reportMapper.selectList(
                        new LambdaQueryWrapper<InterviewReport>()
                                .in(InterviewReport::getSessionId, sessionIds))
                .stream()
                .collect(Collectors.toMap(InterviewReport::getSessionId, report -> report, (first, second) -> first));
        return sessions.stream()
                .map(session -> {
                    InterviewReport report = reports.get(session.getId());
                    return new InterviewHistoryItem(
                            session.getId(),
                            jobTitles.getOrDefault(session.getJobTemplateId(), "未知岗位"),
                            session.getMode(),
                            session.getStatus(),
                            session.getTotalScore(),
                            session.getCreatedAt(),
                            report != null && "SUCCESS".equals(report.getStatus()));
                })
                .toList();
    }

    public StatsOverview stats(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByAsc(InterviewSession::getCreatedAt));
        List<InterviewSession> completed = sessions.stream()
                .filter(session -> "COMPLETED".equals(session.getStatus()))
                .toList();
        List<Integer> scores = completed.stream()
                .map(InterviewSession::getTotalScore)
                .filter(Objects::nonNull)
                .toList();
        double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        int best = scores.stream().mapToInt(Integer::intValue).max().orElse(0);

        List<ScorePoint> trend = completed.stream()
                .filter(session -> session.getTotalScore() != null)
                .map(session -> new ScorePoint(
                        session.getCreatedAt().toLocalDate().toString(),
                        session.getTotalScore(),
                        jobTitle(session.getJobTemplateId())))
                .toList();

        List<InterviewReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getUserId, userId)
                        .eq(InterviewReport::getStatus, "SUCCESS"));
        Map<String, Integer> dimensionAverages = new LinkedHashMap<>();
        for (String key : List.of("accuracy", "depth", "project", "expression", "match")) {
            double avg = reports.stream()
                    .map(report -> readMap(report.getDimensionsJson()).getOrDefault(key, 0))
                    .mapToInt(Integer::intValue)
                    .average().orElse(0);
            dimensionAverages.put(key, (int) Math.round(avg));
        }

        return new StatsOverview(
                sessions.size(),
                completed.size(),
                Math.round(average * 10.0) / 10.0,
                best,
                dimensionAverages,
                trend);
    }

    private void ensureEvaluations(InterviewSession session,
                                   List<InterviewQuestion> questions,
                                   List<InterviewMessage> messages,
                                   List<AnswerEvaluation> evaluations) {
        Set<Long> evaluatedMessageIds = evaluations.stream()
                .map(AnswerEvaluation::getCandidateMessageId)
                .collect(Collectors.toSet());
        Map<Long, InterviewMessage> latestCandidate = new LinkedHashMap<>();
        for (InterviewMessage message : messages) {
            if ("CANDIDATE".equals(message.getRole()) && message.getQuestionId() != null) {
                latestCandidate.put(message.getQuestionId(), message);
            }
        }

        for (InterviewQuestion question : questions) {
            if (!"ANSWERED".equals(question.getStatus())) {
                continue;
            }
            InterviewMessage candidate = latestCandidate.get(question.getId());
            if (candidate == null || evaluatedMessageIds.contains(candidate.getId())) {
                continue;
            }

            String promptType = "QUESTION";
            String promptText = question.getContent();
            for (int i = messages.size() - 1; i >= 0; i--) {
                InterviewMessage message = messages.get(i);
                if ("INTERVIEWER".equals(message.getRole())
                        && question.getId().equals(message.getQuestionId())) {
                    if ("FOLLOW_UP".equals(message.getMessageType())) {
                        promptType = "FOLLOW_UP";
                        promptText = message.getContent();
                    }
                    break;
                }
            }

            AnswerEvaluationResult result = answerEvaluator.evaluate(
                    question, promptText, promptType, candidate.getContent());
            AnswerEvaluation evaluation = new AnswerEvaluation();
            evaluation.setSessionId(session.getId());
            evaluation.setQuestionId(question.getId());
            evaluation.setPromptText(promptText);
            evaluation.setPromptType(promptType);
            evaluation.setCandidateMessageId(candidate.getId());
            evaluation.setCorrectness(result.correctness());
            evaluation.setScore(result.score());
            evaluation.setComment(result.comment());
            evaluation.setMissingPoints(writeJson(result.missingPoints()));
            evaluation.setConfidence(result.confidence());
            evaluationMapper.insert(evaluation);
            evaluations.add(evaluation);
            evaluatedMessageIds.add(candidate.getId());
        }
    }

    private InterviewReportView buildView(InterviewReport report,
                                          InterviewSession session,
                                          List<InterviewQuestion> questions,
                                          List<InterviewMessage> messages,
                                          List<AnswerEvaluation> evaluations) {
        JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
        return new InterviewReportView(
                report.getId(),
                session.getId(),
                job == null ? "未知岗位" : job.getTitle(),
                session.getMode(),
                report.getOverallScore(),
                readMap(report.getDimensionsJson()),
                report.getSummary(),
                readList(report.getStrengthsJson()),
                readList(report.getWeaknessesJson()),
                readList(report.getSuggestionsJson()),
                report.getStatus(),
                report.getCreatedAt(),
                buildQuestionReviews(questions, messages, evaluations));
    }

    private List<QuestionReviewView> buildQuestionReviews(List<InterviewQuestion> questions,
                                                          List<InterviewMessage> messages,
                                                          List<AnswerEvaluation> evaluations) {
        Map<Long, InterviewMessage> latestCandidate = new LinkedHashMap<>();
        for (InterviewMessage message : messages) {
            if ("CANDIDATE".equals(message.getRole()) && message.getQuestionId() != null) {
                latestCandidate.put(message.getQuestionId(), message);
            }
        }
        Map<Long, AnswerEvaluation> evaluationByMessage = evaluations.stream()
                .collect(Collectors.toMap(
                        AnswerEvaluation::getCandidateMessageId,
                        evaluation -> evaluation,
                        (first, second) -> second));

        List<QuestionReviewView> reviews = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (!"ANSWERED".equals(question.getStatus())) {
                continue;
            }
            InterviewMessage candidate = latestCandidate.get(question.getId());
            if (candidate == null) {
                continue;
            }
            AnswerEvaluation evaluation = evaluationByMessage.get(candidate.getId());
            if (evaluation == null) {
                continue;
            }
            reviews.add(new QuestionReviewView(
                    question.getId(),
                    question.getSeq(),
                    question.getType(),
                    question.getContent(),
                    candidate.getContent(),
                    evaluation.getPromptType(),
                    evaluation.getPromptText(),
                    evaluation.getCorrectness(),
                    evaluation.getScore(),
                    evaluation.getComment(),
                    readList(evaluation.getMissingPoints())));
        }
        return reviews;
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试不存在");
        }
        return session;
    }

    private String jobTitle(Long jobTemplateId) {
        JobTemplate job = jobTemplateMapper.selectById(jobTemplateId);
        return job == null ? "未知岗位" : job.getTitle();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报告序列化失败");
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

    private Map<String, Integer> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
