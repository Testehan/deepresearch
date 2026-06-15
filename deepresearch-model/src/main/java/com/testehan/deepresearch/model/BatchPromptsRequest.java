package com.testehan.deepresearch.model;

import java.util.List;

public record BatchPromptsRequest(
        String modelId,
        List<BatchPromptItem> prompts,
        String callerJobId
) {
    public boolean isBatchModel() {
        return modelId != null && modelId.startsWith("batch-");
    }
}
