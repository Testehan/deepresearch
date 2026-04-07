package com.testehan.deepresearch.model;

import java.math.BigDecimal;

public class LlmUsage {

    private int promptTokens;
    private int completionTokens;
    private int cachedTokens;
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    public void add(LlmUsage other) {
        if (other == null) return;
        this.promptTokens += other.promptTokens;
        this.completionTokens += other.completionTokens;
        this.cachedTokens += other.cachedTokens;
        this.totalCostUsd = this.totalCostUsd.add(other.totalCostUsd);
    }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

    public int getCachedTokens() { return cachedTokens; }
    public void setCachedTokens(int cachedTokens) { this.cachedTokens = cachedTokens; }

    public BigDecimal getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
}
