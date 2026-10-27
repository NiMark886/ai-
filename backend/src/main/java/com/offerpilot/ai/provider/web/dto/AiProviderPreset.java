package com.offerpilot.ai.provider.web.dto;

import com.offerpilot.ai.provider.service.AiProviderType;

public record AiProviderPreset(
        String type,
        String label,
        String baseUrl,
        String model
) {

    public static AiProviderPreset from(AiProviderType type) {
        return new AiProviderPreset(type.name(), type.label(), type.defaultBaseUrl(), type.defaultModel());
    }
}
