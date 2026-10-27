package com.offerpilot.ai.service;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.ApiKeyCipher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

@Component
public class AiChatClientFactory {

    private static final String COMPLETIONS_PATH = "/v1/chat/completions";

    private final ApiKeyCipher apiKeyCipher;

    public AiChatClientFactory(ApiKeyCipher apiKeyCipher) {
        this.apiKeyCipher = apiKeyCipher;
    }

    public ChatClient build(AiProvider provider) {
        String baseUrl = normalizeBaseUrl(provider.getBaseUrl());
        String apiKey = apiKeyCipher.decrypt(provider.getApiKeyCipher());

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(COMPLETIONS_PATH)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(provider.getModel())
                .temperature(provider.getTemperature() == null ? 0.4 : provider.getTemperature())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel).build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
}
