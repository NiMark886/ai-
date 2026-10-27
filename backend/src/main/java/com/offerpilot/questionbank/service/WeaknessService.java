package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.AnswerEvaluationMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.questionbank.web.dto.WeaknessView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeaknessService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final QuestionBankRetriever questionBankRetriever;
    private final ObjectMapper objectMapper;

    public WeaknessService(InterviewSessionMapper sessionMapper,
                           InterviewQuestionMapper questionMapper,
                           AnswerEvaluationMapper evaluationMapper,
                           QuestionBankRetriever questionBankRetriever,
                           ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.evaluationMapper = evaluationMapper;
        this.questionBankRetriever = questionBankRetriever;
        this.objectMapper = objectMapper;
    }

    public List<WeaknessView> weaknesses(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt));
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        List<InterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .in(InterviewQuestion::getSessionId, sessionIds));
        Map<Long, List<String>> tagsByQuestion = new LinkedHashMap<>();
        for (InterviewQuestion question : questions) {
            tagsByQuestion.put(question.getId(), readList(question.getKnowledgeTags()));
        }
        Map<Long, LocalDateTime> sessionTimes = new LinkedHashMap<>();
        for (InterviewSession session : sessions) {
            sessionTimes.put(session.getId(), session.getCreatedAt());
        }

        List<AnswerEvaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<AnswerEvaluation>()
                        .in(AnswerEvaluation::getSessionId, sessionIds));

        Map<String, Accumulator> counts = new LinkedHashMap<>();
        for (AnswerEvaluation evaluation : evaluations) {
            boolean weak = !"CORRECT".equals(evaluation.getCorrectness())
                    || (evaluation.getMissingPoints() != null && !evaluation.getMissingPoints().isBlank());
            if (!weak) {
                continue;
            }
            List<String> tags = tagsByQuestion.getOrDefault(evaluation.getQuestionId(), List.of());
            LocalDateTime time = sessionTimes.get(evaluation.getSessionId());
            for (String tag : tags) {
                Accumulator accumulator = counts.computeIfAbsent(tag, key -> new Accumulator());
                accumulator.count++;
                if (time != null && (accumulator.lastSeen == null || time.isAfter(accumulator.lastSeen))) {
                    accumulator.lastSeen = time;
                }
            }
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().count, a.getValue().count))
                .limit(6)
                .map(entry -> new WeaknessView(
                        entry.getKey(),
                        entry.getValue().count,
                        entry.getValue().lastSeen == null ? "" : entry.getValue().lastSeen.toLocalDate().toString(),
                        questionBankRetriever.retrieve(null, List.of(entry.getKey()), 3)))
                .toList();
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

    private static class Accumulator {
        private int count;
        private LocalDateTime lastSeen;
    }
}
