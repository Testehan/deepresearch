package com.testehan.deepresearch.config;

import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
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

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-pro}")
    private String modelName;

    @Value("${spring.ai.google.genai.chat.options.google-search-retrieval:false}")
    private boolean googleSearchRetrieval;

    @Bean
    @Primary
    public ChatModel googleGenAiChatModel() {
        return GoogleGenAiChatModel.builder()
                .genAiClient(Client.builder().apiKey(apiKey).build())
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.1)
                        .googleSearchRetrieval(googleSearchRetrieval)
                        .build())
                .build();
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            @Qualifier("googleGenAiChatModel") ObjectProvider<ChatModel> geminiProvider) {

        if (!useOllama && geminiProvider.getIfAvailable() != null) {
            return ChatClient.builder(geminiProvider.getIfAvailable()).build();
        }
        return builder.build();
    }
}
