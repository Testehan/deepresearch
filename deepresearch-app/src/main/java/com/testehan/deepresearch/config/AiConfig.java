package com.testehan.deepresearch.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean("ollamaChatClient")
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        return ChatClient.builder(ollamaModel).build();
    }

    @Bean("geminiChatClient")
    public ChatClient geminiChatClient(@Qualifier("googleGenAiChatModel") ChatModel geminiModel) {
        return ChatClient.builder(geminiModel).build();
    }
}
