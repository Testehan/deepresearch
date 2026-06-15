package com.testehan.deepresearch.model;

public record BatchPromptResult(
        String sectionId,
        String text,
        int promptTokens,
        int completionTokens,
        int cachedTokens,
        int thoughtsTokens,
        int toolUsePromptTokens,
        int totalTokens
) {}
