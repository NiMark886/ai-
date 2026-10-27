package com.offerpilot.questionbank.service;

import com.offerpilot.questionbank.web.dto.QuestionBankItem;

import java.util.List;

public interface QuestionBankRetriever {

    List<QuestionBankItem> retrieve(String query, List<String> tags, int limit);
}
