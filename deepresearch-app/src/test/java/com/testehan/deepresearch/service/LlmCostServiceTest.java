package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.LlmUsage;
import com.testehan.deepresearch.pipeline.BatchGeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class LlmCostServiceTest {

    private final LlmCostService llmCostService = new LlmCostService();

    @Test
    void extractUsage_GeminiThoughtsTokens_ArePersistedAndBilledAsOutput() {
        ChatResponse response = buildResponse(1000, 500, 0, 2000, 25, 3525);

        LlmUsage usage = llmCostService.extractUsage(response, "gemini-2.5-pro");

        assertEquals(1000, usage.getPromptTokens());
        assertEquals(500, usage.getCompletionTokens());
        assertEquals(2000, usage.getThoughtsTokens());
        assertEquals(25, usage.getToolUsePromptTokens());
        assertEquals(3525, usage.getTotalTokens());
        assertEquals(new BigDecimal("0.026281"), usage.getTotalCostUsd());
    }

    @Test
    void logAndAccumulateBatch_IncludesThoughtsAndToolUseTokens() {
        LlmUsage accumulator = new LlmUsage();
        List<BatchGeminiService.BatchResult> results = List.of(
                new BatchGeminiService.BatchResult(null, "one", 1000, 100, 0, 200, 10, 1310),
                new BatchGeminiService.BatchResult(null, "two", 2000, 300, 100, 400, 20, 2720)
        );

        llmCostService.logAndAccumulateBatch(results, "gemini-2.5-pro", accumulator);

        assertEquals(3000, accumulator.getPromptTokens());
        assertEquals(400, accumulator.getCompletionTokens());
        assertEquals(100, accumulator.getCachedTokens());
        assertEquals(600, accumulator.getThoughtsTokens());
        assertEquals(30, accumulator.getToolUsePromptTokens());
        assertEquals(4030, accumulator.getTotalTokens());
    }

    @Test
    void llmUsageAdd_IncludesExtendedTokenFields() {
        LlmUsage first = new LlmUsage();
        first.setPromptTokens(100);
        first.setCompletionTokens(200);
        first.setCachedTokens(10);
        first.setThoughtsTokens(300);
        first.setToolUsePromptTokens(20);
        first.setTotalTokens(620);
        first.setTotalCostUsd(new BigDecimal("0.01"));

        LlmUsage second = new LlmUsage();
        second.setPromptTokens(1);
        second.setCompletionTokens(2);
        second.setCachedTokens(3);
        second.setThoughtsTokens(4);
        second.setToolUsePromptTokens(5);
        second.setTotalTokens(12);
        second.setTotalCostUsd(new BigDecimal("0.02"));

        first.add(second);

        assertEquals(101, first.getPromptTokens());
        assertEquals(202, first.getCompletionTokens());
        assertEquals(13, first.getCachedTokens());
        assertEquals(304, first.getThoughtsTokens());
        assertEquals(25, first.getToolUsePromptTokens());
        assertEquals(632, first.getTotalTokens());
        assertEquals(new BigDecimal("0.03"), first.getTotalCostUsd());
    }

    private ChatResponse buildResponse(int promptTokens, int completionTokens, int cachedTokens,
                                       int thoughtsTokens, int toolUsePromptTokens, int totalTokens) {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        GoogleGenAiUsage usage = mock(GoogleGenAiUsage.class);

        lenient().when(usage.getPromptTokens()).thenReturn(promptTokens);
        lenient().when(usage.getCompletionTokens()).thenReturn(completionTokens);
        lenient().when(usage.getCachedContentTokenCount()).thenReturn(cachedTokens);
        lenient().when(usage.getThoughtsTokenCount()).thenReturn(thoughtsTokens);
        lenient().when(usage.getToolUsePromptTokenCount()).thenReturn(toolUsePromptTokens);
        lenient().when(usage.getTotalTokens()).thenReturn(totalTokens);
        lenient().when(metadata.getUsage()).thenReturn(usage);
        lenient().when(response.getMetadata()).thenReturn(metadata);
        return response;
    }
}
