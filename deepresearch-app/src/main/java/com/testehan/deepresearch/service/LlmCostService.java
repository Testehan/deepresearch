package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.LlmUsage;
import com.testehan.deepresearch.pipeline.BatchGeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class LlmCostService {

    private static final Logger log = LoggerFactory.getLogger(LlmCostService.class);

    private static final int LARGE_TOKEN_THRESHOLD = 200_000;
    private static final int MILLION = 1_000_000;
    private static final BigDecimal BATCH_DISCOUNT = new BigDecimal("0.75"); // 25% off

    private record ModelPricing(
            BigDecimal inputSmall, BigDecimal inputLarge,
            BigDecimal outputSmall, BigDecimal outputLarge,
            BigDecimal cacheSmall, BigDecimal cacheLarge) {

        static ModelPricing flat(BigDecimal input, BigDecimal output, BigDecimal cache) {
            return new ModelPricing(input, input, output, output, cache, cache);
        }

        ModelPricing withBatchDiscount() {
            return new ModelPricing(
                    inputSmall.multiply(BATCH_DISCOUNT),  inputLarge.multiply(BATCH_DISCOUNT),
                    outputSmall.multiply(BATCH_DISCOUNT), outputLarge.multiply(BATCH_DISCOUNT),
                    cacheSmall.multiply(BATCH_DISCOUNT),  cacheLarge.multiply(BATCH_DISCOUNT));
        }
    }

    private static final ModelPricing GEMINI_25_PRO = new ModelPricing(
            new BigDecimal("1.25"),  new BigDecimal("2.50"),
            new BigDecimal("10.00"), new BigDecimal("15.00"),
            new BigDecimal("0.125"), new BigDecimal("0.25"));

    private static final ModelPricing GEMINI_31_FLASH_LITE = ModelPricing.flat(
            new BigDecimal("0.10"), new BigDecimal("0.40"), new BigDecimal("0.025"));

    private static final ModelPricing ZERO = ModelPricing.flat(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private ModelPricing pricingFor(String modelId) {
        if (modelId == null) return ZERO;
        return switch (modelId) {
            case "gemini-2.5-pro"              -> GEMINI_25_PRO;
            case "gemini-3.1-flash-lite"       -> GEMINI_31_FLASH_LITE;
            case "batch-gemini-2.5-pro"        -> GEMINI_25_PRO.withBatchDiscount();
            case "batch-gemini-3.1-flash-lite" -> GEMINI_31_FLASH_LITE.withBatchDiscount();
            default                            -> ZERO; // ollama and unknowns are free
        };
    }

    public LlmUsage extractUsage(ChatResponse response, String modelId) {
        LlmUsage usage = new LlmUsage();
        if (response == null) return usage;

        int promptTokens     = extractPromptTokens(response);
        int completionTokens = extractCompletionTokens(response);
        int cachedTokens     = extractCachedTokens(response);

        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setCachedTokens(cachedTokens);
        usage.setTotalCostUsd(calculateCost(promptTokens, completionTokens, cachedTokens, modelId));
        return usage;
    }

    public void logAndAccumulate(ChatResponse response, String operationType, LlmUsage accumulator, String modelId) {
        LlmUsage delta = extractUsage(response, modelId);
        accumulator.add(delta);
        log.info("LLM usage [{}]: prompt={} completion={} cached={} cost=${}",
                operationType,
                delta.getPromptTokens(),
                delta.getCompletionTokens(),
                delta.getCachedTokens(),
                delta.getTotalCostUsd());
    }

    public void logAndAccumulateBatch(List<BatchGeminiService.BatchResult> results, String modelId, LlmUsage accumulator) {
        int totalPrompt = 0, totalCompletion = 0, totalCached = 0;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (BatchGeminiService.BatchResult r : results) {
            totalPrompt     += r.promptTokens();
            totalCompletion += r.completionTokens();
            totalCached     += r.cachedTokens();
            totalCost        = totalCost.add(calculateCost(r.promptTokens(), r.completionTokens(), r.cachedTokens(), modelId));
        }

        LlmUsage delta = new LlmUsage();
        delta.setPromptTokens(totalPrompt);
        delta.setCompletionTokens(totalCompletion);
        delta.setCachedTokens(totalCached);
        delta.setTotalCostUsd(totalCost.setScale(6, RoundingMode.HALF_UP));
        accumulator.add(delta);

        log.info("LLM usage [batch_chunks x{}]: prompt={} completion={} cached={} cost=${}",
                results.size(), totalPrompt, totalCompletion, totalCached, totalCost);
    }

    public BigDecimal calculateCost(int promptTokens, int completionTokens, int cachedTokens, String modelId) {
        ModelPricing p = pricingFor(modelId);
        return calculatePromptCost(promptTokens, cachedTokens, p)
                .add(calculateOutputCost(completionTokens, p))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePromptCost(int promptTokens, int cachedTokens, ModelPricing p) {
        BigDecimal inputPrice = promptTokens > LARGE_TOKEN_THRESHOLD ? p.inputLarge() : p.inputSmall();
        BigDecimal cachePrice = cachedTokens > LARGE_TOKEN_THRESHOLD ? p.cacheLarge() : p.cacheSmall();
        int nonCachedTokens = Math.max(0, promptTokens - cachedTokens);
        BigDecimal nonCachedCost = BigDecimal.valueOf(nonCachedTokens)
                .multiply(inputPrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);
        BigDecimal cachedCost = BigDecimal.valueOf(cachedTokens)
                .multiply(cachePrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);
        return nonCachedCost.add(cachedCost);
    }

    private BigDecimal calculateOutputCost(int completionTokens, ModelPricing p) {
        BigDecimal outputPrice = completionTokens > LARGE_TOKEN_THRESHOLD ? p.outputLarge() : p.outputSmall();
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
