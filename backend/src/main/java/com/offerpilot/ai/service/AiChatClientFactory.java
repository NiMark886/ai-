package com.offerpilot.ai.service;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.ApiKeyCipher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class AiChatClientFactory {

    private static final String COMPLETIONS_PATH = "/v1/chat/completions";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    private final ApiKeyCipher apiKeyCipher;

    public AiChatClientFactory(ApiKeyCipher apiKeyCipher) {
        this.apiKeyCipher = apiKeyCipher;
    }

    public ChatClient build(AiProvider provider) {
        return build(provider, DEFAULT_READ_TIMEOUT);
    }

    public ChatClient build(AiProvider provider, Duration readTimeout) {
        String baseUrl = normalizeBaseUrl(provider.getBaseUrl());
        String apiKey = apiKeyCipher.decrypt(provider.getApiKeyCipher());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(COMPLETIONS_PATH)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
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
