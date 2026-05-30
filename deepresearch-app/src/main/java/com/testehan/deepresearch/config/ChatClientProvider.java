package com.testehan.deepresearch.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ChatClientProvider {

    private final ChatClient ollamaChatClient;
    private final ChatClient geminiChatClient;

    ChatClientProvider(
            @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
            @Qualifier("geminiChatClient") ChatClient geminiChatClient) {
        this.ollamaChatClient = ollamaChatClient;
        this.geminiChatClient = geminiChatClient;
    }

    public ChatClient chatClientFor(String modelId) {
        if ("ollama".equals(modelId) || modelId == null) return ollamaChatClient;
        return geminiChatClient;
    }

    /** Returns null for Ollama (model is set in application.properties). */
    public GoogleGenAiChatOptions optionsFor(String modelId) {
        if ("ollama".equals(modelId) || modelId == null) return null;
        String model = modelId.startsWith("batch-") ? modelId.substring(6) : modelId;
        return GoogleGenAiChatOptions.builder().model(model).build();
    }

    public boolean isBatch(String modelId) {
        return modelId != null && modelId.startsWith("batch-");
    }
}
