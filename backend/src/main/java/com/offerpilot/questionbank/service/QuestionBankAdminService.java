package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.questionbank.domain.QuestionBank;
import com.offerpilot.questionbank.mapper.QuestionBankMapper;
import com.offerpilot.questionbank.web.dto.PageResult;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminRequest;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminView;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.TrackQuestion;
import com.offerpilot.track.mapper.InterviewTrackMapper;
import com.offerpilot.track.mapper.TrackQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class QuestionBankAdminService {

    private final QuestionBankMapper questionBankMapper;
    private final TrackQuestionMapper trackQuestionMapper;
    private final InterviewTrackMapper trackMapper;

    public QuestionBankAdminService(QuestionBankMapper questionBankMapper,
                                    TrackQuestionMapper trackQuestionMapper,
                                    InterviewTrackMapper trackMapper) {
        this.questionBankMapper = questionBankMapper;
        this.trackQuestionMapper = trackQuestionMapper;
        this.trackMapper = trackMapper;
    }

    public PageResult<QuestionBankAdminView> page(long page, long size, String keyword) {
        return page(page, size, keyword, null);
    }

    public PageResult<QuestionBankAdminView> page(long page, long size, String keyword, Long trackId) {
        long current = Math.max(1, page);
        long pageSize = Math.min(50, Math.max(1, size));
        Page<QuestionBank> request = new Page<>(current, pageSize);
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query -> query
                    .like(QuestionBank::getQuestion, keyword)
                    .or()
                    .like(QuestionBank::getCategory, keyword)
                    .or()
                    .like(QuestionBank::getTags, keyword));
        }
        if (trackId != null) {
            List<Long> questionIds = trackQuestionMapper.selectList(new LambdaQueryWrapper<TrackQuestion>()
                            .eq(TrackQuestion::getTrackId, trackId)
                            .eq(TrackQuestion::getEnabled, 1))
                    .stream()
                    .map(TrackQuestion::getQuestionId)
                    .distinct()
                    .toList();
            if (questionIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, current, pageSize);
            }
            wrapper.in(QuestionBank::getId, questionIds);
        }
        wrapper.orderByDesc(QuestionBank::getId);
        Page<QuestionBank> result = questionBankMapper.selectPage(request, wrapper);
        return new PageResult<>(
                toViews(result.getRecords()),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public QuestionBankAdminView get(Long id) {
        return toView(load(id));
    }

    @Transactional
    public QuestionBankAdminView create(QuestionBankAdminRequest request) {
        QuestionBank bank = new QuestionBank();
        apply(bank, request);
        questionBankMapper.insert(bank);
        if (request.trackIds() != null) {
            replaceTrackBindings(bank.getId(), request.trackIds());
        }
        return toView(bank);
    }

    @Transactional
    public QuestionBankAdminView update(Long id, QuestionBankAdminRequest request) {
        QuestionBank bank = load(id);
        apply(bank, request);
        questionBankMapper.updateById(bank);
        if (request.trackIds() != null) {
            replaceTrackBindings(id, request.trackIds());
        }
        return toView(bank);
    }

    @Transactional
    public void delete(Long id) {
        load(id);
        trackQuestionMapper.delete(new LambdaQueryWrapper<TrackQuestion>()
                .eq(TrackQuestion::getQuestionId, id));
        questionBankMapper.deleteById(id);
    }

    private QuestionBankAdminView toView(QuestionBank bank) {
        return toViews(List.of(bank)).get(0);
    }

    private List<QuestionBankAdminView> toViews(List<QuestionBank> banks) {
        if (banks.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = banks.stream().map(QuestionBank::getId).toList();
        List<TrackQuestion> bindings = trackQuestionMapper.selectList(new LambdaQueryWrapper<TrackQuestion>()
                .in(TrackQuestion::getQuestionId, questionIds));

        Map<Long, List<Long>> trackIdsByQuestion = new HashMap<>();
        for (TrackQuestion binding : bindings) {
            trackIdsByQuestion.computeIfAbsent(binding.getQuestionId(), key -> new ArrayList<>())
                    .add(binding.getTrackId());
        }
        List<Long> allTrackIds = bindings.stream()
                .map(TrackQuestion::getTrackId)
                .distinct()
                .toList();
        Map<Long, InterviewTrack> tracks = allTrackIds.isEmpty() ? Map.of()
                : trackMapper.selectBatchIds(allTrackIds).stream()
                .collect(java.util.stream.Collectors.toMap(InterviewTrack::getId, track -> track));

        return banks.stream()
                .map(bank -> {
                    List<Long> trackIds = trackIdsByQuestion.getOrDefault(bank.getId(), List.of())
                            .stream()
                            .sorted(Comparator.comparingInt(trackId -> {
                                InterviewTrack track = tracks.get(trackId);
                                return track == null || track.getSortOrder() == null ? 0 : track.getSortOrder();
                            }))
                            .toList();
                    List<String> trackNames = trackIds.stream()
                            .map(trackId -> {
                                InterviewTrack track = tracks.get(trackId);
                                return track == null ? "未知题库" : track.getName();
                            })
                            .toList();
                    return QuestionBankAdminView.from(bank, trackIds, trackNames);
                })
                .toList();
    }

    private void replaceTrackBindings(Long questionId, List<Long> trackIds) {
        List<Long> distinct = trackIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!distinct.isEmpty()) {
            List<InterviewTrack> tracks = trackMapper.selectBatchIds(distinct);
            if (tracks.size() != distinct.size()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "题库不存在");
            }
        }
        trackQuestionMapper.delete(new LambdaQueryWrapper<TrackQuestion>()
                .eq(TrackQuestion::getQuestionId, questionId));
        for (Long trackId : distinct) {
            TrackQuestion binding = new TrackQuestion();
            binding.setTrackId(trackId);
            binding.setQuestionId(questionId);
            binding.setSortOrder(0);
            binding.setWeight(100);
            binding.setEnabled(1);
            trackQuestionMapper.insert(binding);
        }
    }

    private QuestionBank load(Long id) {
        QuestionBank bank = questionBankMapper.selectById(id);
        if (bank == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return bank;
    }

    private void apply(QuestionBank bank, QuestionBankAdminRequest request) {
        bank.setCategory(request.category().trim());
        bank.setLevel(request.level().trim());
        bank.setTags(normalizeTags(request.tags()));
        bank.setDifficulty(request.difficulty());
        bank.setQuestion(request.question().trim());
        bank.setReferenceAnswer(request.referenceAnswer().trim());
        bank.setSource(request.source());
        String status = request.status() == null ? "ACTIVE" : request.status().toUpperCase(Locale.ROOT);
        bank.setStatus("INACTIVE".equals(status) ? "INACTIVE" : "ACTIVE");
    }

    private String normalizeTags(String tags) {
        List<String> values = Arrays.stream(tags.split("[,，、]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return String.join(",", values);
    }
}
