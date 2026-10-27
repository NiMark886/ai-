package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.questionbank.domain.QuestionBank;
import com.offerpilot.questionbank.mapper.QuestionBankMapper;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class KeywordQuestionBankRetriever implements QuestionBankRetriever {

    private final QuestionBankMapper questionBankMapper;

    public KeywordQuestionBankRetriever(QuestionBankMapper questionBankMapper) {
        this.questionBankMapper = questionBankMapper;
    }

    @Override
    public List<QuestionBankItem> retrieve(String query, List<String> tags, int limit) {
        List<QuestionBank> questions = questionBankMapper.selectList(
                new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getStatus, "ACTIVE"));
        List<String> tagTokens = tags == null ? List.of() : tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> queryTokens = new ArrayList<>();
        if (query != null) {
            for (String token : query.toLowerCase(Locale.ROOT).split("[\\s,，、;；/]+")) {
                if (token.length() >= 2) {
                    queryTokens.add(token);
                }
            }
        }
        int size = Math.max(1, limit);
        return questions.stream()
                .map(question -> new Scored(question, score(question, tagTokens, queryTokens)))
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(size)
                .map(scored -> QuestionBankItem.from(scored.question()))
                .toList();
    }

    private int score(QuestionBank question, List<String> tags, List<String> queryTokens) {
        String text = (question.getQuestion() + " " + question.getReferenceAnswer() + " "
                + question.getTags() + " " + question.getCategory()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String tag : tags) {
            if (text.contains(tag.toLowerCase(Locale.ROOT))) {
                score += 5;
            }
        }
        for (String token : queryTokens) {
            if (text.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private record Scored(QuestionBank question, int score) {
    }
}
