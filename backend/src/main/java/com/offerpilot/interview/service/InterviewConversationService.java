package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.interview.mapper.InterviewMessageMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerResult;
import com.offerpilot.interview.web.dto.HintResult;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.MessageView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewConversationService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewService interviewService;
    private final InterviewerReplyGenerator replyGenerator;
    private final ObjectMapper objectMapper;

    public InterviewConversationService(InterviewSessionMapper sessionMapper,
                                        InterviewQuestionMapper questionMapper,
                                        InterviewMessageMapper messageMapper,
                                        InterviewService interviewService,
                                        InterviewerReplyGenerator replyGenerator,
                                        ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.interviewService = interviewService;
        this.replyGenerator = replyGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionView start(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            return interviewService.get(userId, sessionId);
        }
        if ("READY".equals(session.getStatus())) {
            session.setStatus("IN_PROGRESS");
            session.setStartedAt(LocalDateTime.now());
            InterviewQuestion first = nextPending(sessionId);
            if (first == null) {
                complete(session);
                addMessage(sessionId, null, "INTERVIEWER", "CLOSING",
                        replyGenerator.generate(null, ReplyType.CLOSING, null, List.of()));
                sessionMapper.updateById(session);
                return interviewService.get(userId, sessionId);
            }
            ask(session, first);
            addMessage(sessionId, first.getId(), "INTERVIEWER", "OPENING",
                    replyGenerator.generate(first, ReplyType.OPENING, null, List.of()));
            sessionMapper.updateById(session);
        }
        return interviewService.get(userId, sessionId);
    }

    public List<MessageView> messages(Long userId, Long sessionId) {
        loadOwned(userId, sessionId);
        return history(sessionId).stream()
                .map(MessageView::from)
                .toList();
    }

    @Transactional
    public AnswerResult answer(Long userId, Long sessionId, AnswerRequest request) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前面试不在进行中");
        }

        InterviewQuestion question = questionMapper.selectById(request.questionId());
        if (question == null || !sessionId.equals(question.getSessionId())
                || !"ASKED".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前题目不可回答");
        }

        InterviewMessage candidate = addMessage(sessionId, question.getId(), "CANDIDATE", "ANSWER", request.content());
        List<InterviewMessage> historyMessages = history(sessionId);

        if (shouldFollowUp(question, request.content())) {
            int count = question.getFollowUpCount() == null ? 0 : question.getFollowUpCount();
            question.setFollowUpCount(count + 1);
            questionMapper.updateById(question);
            String reply = replyGenerator.generate(question, ReplyType.FOLLOW_UP, request.content(), historyMessages);
            InterviewMessage interviewer = addMessage(sessionId, question.getId(), "INTERVIEWER", "FOLLOW_UP", reply);
            return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                    "FOLLOW_UP", reply, session.getStatus(), question.getId(), false);
        }

        question.setStatus("ANSWERED");
        question.setAnsweredAt(LocalDateTime.now());
        questionMapper.updateById(question);

        InterviewQuestion next = nextPending(sessionId);
        if (next != null) {
            ask(session, next);
            String reply = replyGenerator.generate(next, ReplyType.QUESTION, request.content(), historyMessages);
            InterviewMessage interviewer = addMessage(sessionId, next.getId(), "INTERVIEWER", "QUESTION", reply);
            sessionMapper.updateById(session);
            return new AnswerResult(candidate.getId(), interviewer.getId(), next.getId(),
                    "QUESTION", reply, session.getStatus(), next.getId(), false);
        }

        complete(session);
        sessionMapper.updateById(session);
        String reply = replyGenerator.generate(question, ReplyType.CLOSING, request.content(), historyMessages);
        InterviewMessage interviewer = addMessage(sessionId, question.getId(), "INTERVIEWER", "CLOSING", reply);
        return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                "CLOSING", reply, "COMPLETED", null, true);
    }

    @Transactional
    public HintResult hint(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus()) || session.getCurrentQuestionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前没有可提示的题目");
        }
        InterviewQuestion question = questionMapper.selectById(session.getCurrentQuestionId());
        if (question == null || !"ASKED".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前没有可提示的题目");
        }
        int count = question.getHintCount() == null ? 0 : question.getHintCount();
        if (count >= 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "本题已使用过提示");
        }
        question.setHintCount(count + 1);
        questionMapper.updateById(question);

        List<String> points = readList(question.getReferencePoints());
        String hint = points.isEmpty() ? "先讲清楚背景、你的职责和最终结果。" : "可以从「" + points.get(0) + "」入手。";
        InterviewMessage message = addMessage(sessionId, question.getId(), "SYSTEM", "HINT", "提示：" + hint);
        return new HintResult(message.getId(), hint);
    }

    @Transactional
    public InterviewSessionView skip(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前面试不在进行中");
        }
        InterviewQuestion current = session.getCurrentQuestionId() == null
                ? null : questionMapper.selectById(session.getCurrentQuestionId());
        if (current != null && "ASKED".equals(current.getStatus())) {
            current.setStatus("SKIPPED");
            current.setAnsweredAt(LocalDateTime.now());
            questionMapper.updateById(current);
        }

        InterviewQuestion next = nextPending(sessionId);
        if (next != null) {
            ask(session, next);
            addMessage(sessionId, next.getId(), "INTERVIEWER", "QUESTION",
                    replyGenerator.generate(next, ReplyType.QUESTION, null, history(sessionId)));
            sessionMapper.updateById(session);
            return interviewService.get(userId, sessionId);
        }

        complete(session);
        sessionMapper.updateById(session);
        addMessage(sessionId, current == null ? null : current.getId(), "INTERVIEWER", "CLOSING",
                replyGenerator.generate(current, ReplyType.CLOSING, null, history(sessionId)));
        return interviewService.get(userId, sessionId);
    }

    @Transactional
    public InterviewSessionView finish(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            return interviewService.get(userId, sessionId);
        }
        InterviewQuestion current = session.getCurrentQuestionId() == null
                ? null : questionMapper.selectById(session.getCurrentQuestionId());
        if (current != null && "ASKED".equals(current.getStatus())) {
            current.setStatus("SKIPPED");
            current.setAnsweredAt(LocalDateTime.now());
            questionMapper.updateById(current);
        }
        complete(session);
        sessionMapper.updateById(session);
        addMessage(sessionId, current == null ? null : current.getId(), "INTERVIEWER", "CLOSING",
                replyGenerator.generate(current, ReplyType.CLOSING, null, history(sessionId)));
        return interviewService.get(userId, sessionId);
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试不存在");
        }
        return session;
    }

    private List<InterviewMessage> history(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getId));
    }

    private InterviewQuestion nextPending(Long sessionId) {
        return questionMapper.selectOne(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .eq(InterviewQuestion::getStatus, "PENDING")
                .orderByAsc(InterviewQuestion::getSeq)
                .last("LIMIT 1"));
    }

    private void ask(InterviewSession session, InterviewQuestion question) {
        question.setStatus("ASKED");
        question.setAskedAt(LocalDateTime.now());
        questionMapper.updateById(question);
        session.setCurrentQuestionId(question.getId());
    }

    private void complete(InterviewSession session) {
        session.setStatus("COMPLETED");
        session.setEndedAt(LocalDateTime.now());
        session.setCurrentQuestionId(null);
    }

    private boolean shouldFollowUp(InterviewQuestion question, String answer) {
        int count = question.getFollowUpCount() == null ? 0 : question.getFollowUpCount();
        if (count >= 2) {
            return false;
        }
        if (readList(question.getFollowUpHints()).isEmpty()) {
            return false;
        }
        String text = answer == null ? "" : answer.trim();
        return text.length() < 60
                || text.contains("不知道")
                || text.contains("不太清楚")
                || text.contains("没做过")
                || text.contains("大概")
                || text.contains("可能");
    }

    private InterviewMessage addMessage(Long sessionId, Long questionId, String role,
                                        String messageType, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setQuestionId(questionId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        messageMapper.insert(message);
        return message;
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
