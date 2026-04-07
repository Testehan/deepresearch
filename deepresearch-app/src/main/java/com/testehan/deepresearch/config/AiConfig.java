package com.testehan.deepresearch.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Value("${app.llm.use-ollama:true}")
    private boolean useOllama;

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("googleGenAiChatModel") ObjectProvider<ChatModel> geminiProvider,
            @Qualifier("ollamaChatModel") ObjectProvider<ChatModel> ollamaProvider) {
        if (!useOllama) {
            ChatModel gemini = geminiProvider.getIfAvailable();
            if (gemini != null) return gemini;
        }
        return ollamaProvider.getObject();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
