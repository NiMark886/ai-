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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class QuestionBankAdminService {

    private final QuestionBankMapper questionBankMapper;

    public QuestionBankAdminService(QuestionBankMapper questionBankMapper) {
        this.questionBankMapper = questionBankMapper;
    }

    public PageResult<QuestionBankAdminView> page(long page, long size, String keyword) {
        Page<QuestionBank> request = new Page<>(Math.max(1, page), Math.min(50, Math.max(1, size)));
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query -> query
                    .like(QuestionBank::getQuestion, keyword)
                    .or()
                    .like(QuestionBank::getCategory, keyword)
                    .or()
                    .like(QuestionBank::getTags, keyword));
        }
        wrapper.orderByDesc(QuestionBank::getId);
        Page<QuestionBank> result = questionBankMapper.selectPage(request, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(QuestionBankAdminView::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public QuestionBankAdminView get(Long id) {
        return QuestionBankAdminView.from(load(id));
    }

    @Transactional
    public QuestionBankAdminView create(QuestionBankAdminRequest request) {
        QuestionBank bank = new QuestionBank();
        apply(bank, request);
        questionBankMapper.insert(bank);
        return QuestionBankAdminView.from(bank);
    }

    @Transactional
    public QuestionBankAdminView update(Long id, QuestionBankAdminRequest request) {
        QuestionBank bank = load(id);
        apply(bank, request);
        questionBankMapper.updateById(bank);
        return QuestionBankAdminView.from(bank);
    }

    @Transactional
    public void delete(Long id) {
        load(id);
        questionBankMapper.deleteById(id);
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
