package com.testehan.deepresearch.model;

import java.math.BigDecimal;

public class LlmUsage {

    private int promptTokens;
    private int completionTokens;
    private int cachedTokens;
    private int thoughtsTokens;
    private int toolUsePromptTokens;
    private int totalTokens;
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    public void add(LlmUsage other) {
        if (other == null) return;
        this.promptTokens += other.promptTokens;
        this.completionTokens += other.completionTokens;
        this.cachedTokens += other.cachedTokens;
        this.thoughtsTokens += other.thoughtsTokens;
        this.toolUsePromptTokens += other.toolUsePromptTokens;
        this.totalTokens += other.totalTokens;
        this.totalCostUsd = this.totalCostUsd.add(other.totalCostUsd);
    }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

    public int getCachedTokens() { return cachedTokens; }
    public void setCachedTokens(int cachedTokens) { this.cachedTokens = cachedTokens; }

    public int getThoughtsTokens() { return thoughtsTokens; }
    public void setThoughtsTokens(int thoughtsTokens) { this.thoughtsTokens = thoughtsTokens; }

    public int getToolUsePromptTokens() { return toolUsePromptTokens; }
    public void setToolUsePromptTokens(int toolUsePromptTokens) { this.toolUsePromptTokens = toolUsePromptTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public BigDecimal getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
}
