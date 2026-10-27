package com.offerpilot.ai.service;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.AiProviderService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ChatClientProvider {

    private final ChatClient defaultChatClient;
    private final AiProviderService providerService;
    private final AiChatClientFactory chatClientFactory;

    public ChatClientProvider(ChatClient.Builder builder,
                              AiProviderService providerService,
                              AiChatClientFactory chatClientFactory) {
        this.defaultChatClient = builder.build();
        this.providerService = providerService;
        this.chatClientFactory = chatClientFactory;
    }

    public ChatClient get() {
        Long userId = currentUserId();
        if (userId != null) {
            AiProvider provider = providerService.activeForUser(userId);
            if (provider != null) {
                return chatClientFactory.build(provider);
            }
        }
        return defaultChatClient;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
