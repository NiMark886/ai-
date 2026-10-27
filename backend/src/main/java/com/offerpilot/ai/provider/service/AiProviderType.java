package com.offerpilot.ai.provider.service;

import java.util.Locale;

public enum AiProviderType {

    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    MIMO("小米 MiMo", "https://api.xiaomimimo.com/v1", "mimo-v2.5"),
    MIMO_TOKEN_PLAN("小米 MiMo Token Plan", "https://token-plan-cn.xiaomimimo.com/v1", "mimo-v2.5"),
    CUSTOM("自定义（OpenAI 兼容）", "", "");

    private final String label;
    private final String defaultBaseUrl;
    private final String defaultModel;

    AiProviderType(String label, String defaultBaseUrl, String defaultModel) {
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
    }

    public String label() {
        return label;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public static AiProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return CUSTOM;
        }
        try {
            return AiProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CUSTOM;
        }
    }
}
