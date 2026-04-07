package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.LlmUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class LlmCostService {

    private static final Logger log = LoggerFactory.getLogger(LlmCostService.class);

    private static final BigDecimal INPUT_PRICE_SMALL  = new BigDecimal("1.25");
    private static final BigDecimal INPUT_PRICE_LARGE  = new BigDecimal("2.50");
    private static final BigDecimal OUTPUT_PRICE_SMALL = new BigDecimal("10.00");
    private static final BigDecimal OUTPUT_PRICE_LARGE = new BigDecimal("15.00");
    private static final BigDecimal CACHE_PRICE_SMALL  = new BigDecimal("0.125");
    private static final BigDecimal CACHE_PRICE_LARGE  = new BigDecimal("0.25");

    private static final int LARGE_TOKEN_THRESHOLD = 200_000;
    private static final int MILLION = 1_000_000;

    public LlmUsage extractUsage(ChatResponse response) {
        LlmUsage usage = new LlmUsage();
        if (response == null) return usage;

        int promptTokens     = extractPromptTokens(response);
        int completionTokens = extractCompletionTokens(response);
        int cachedTokens     = extractCachedTokens(response);

        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setCachedTokens(cachedTokens);
        usage.setTotalCostUsd(calculateCost(promptTokens, completionTokens, cachedTokens));
        return usage;
    }

    public void logAndAccumulate(ChatResponse response, String operationType, LlmUsage accumulator) {
        LlmUsage delta = extractUsage(response);
        accumulator.add(delta);
        log.info("LLM usage [{}]: prompt={} completion={} cached={} cost=${}",
                operationType,
                delta.getPromptTokens(),
                delta.getCompletionTokens(),
                delta.getCachedTokens(),
                delta.getTotalCostUsd());
    }

    public BigDecimal calculateCost(int promptTokens, int completionTokens, int cachedTokens) {
        return calculatePromptCost(promptTokens, cachedTokens)
                .add(calculateOutputCost(completionTokens))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePromptCost(int promptTokens, int cachedTokens) {
        BigDecimal inputPrice = promptTokens > LARGE_TOKEN_THRESHOLD ? INPUT_PRICE_LARGE : INPUT_PRICE_SMALL;
        BigDecimal cachePrice = cachedTokens > LARGE_TOKEN_THRESHOLD ? CACHE_PRICE_LARGE : CACHE_PRICE_SMALL;

        int nonCachedTokens = Math.max(0, promptTokens - cachedTokens);
        BigDecimal nonCachedCost = BigDecimal.valueOf(nonCachedTokens)
                .multiply(inputPrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);
        BigDecimal cachedCost = BigDecimal.valueOf(cachedTokens)
                .multiply(cachePrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);
        return nonCachedCost.add(cachedCost);
    }

    private BigDecimal calculateOutputCost(int completionTokens) {
        BigDecimal outputPrice = completionTokens > LARGE_TOKEN_THRESHOLD ? OUTPUT_PRICE_LARGE : OUTPUT_PRICE_SMALL;
        return BigDecimal.valueOf(completionTokens)
                .multiply(outputPrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);
    }

    private int extractPromptTokens(ChatResponse response) {
        if (response.getMetadata() == null) return 0;
        var usage = response.getMetadata().getUsage();
        return usage == null ? 0 : usage.getPromptTokens();
    }

    private int extractCompletionTokens(ChatResponse response) {
        if (response.getMetadata() == null) return 0;
        var usage = response.getMetadata().getUsage();
        return usage == null ? 0 : usage.getCompletionTokens();
    }

    private int extractCachedTokens(ChatResponse response) {
        if (response.getMetadata() == null) return 0;
        var usage = response.getMetadata().getUsage();
        if (!(usage instanceof GoogleGenAiUsage googleUsage)) return 0;
        Integer cached = googleUsage.getCachedContentTokenCount();
        return cached != null ? cached : 0;
    }
}
