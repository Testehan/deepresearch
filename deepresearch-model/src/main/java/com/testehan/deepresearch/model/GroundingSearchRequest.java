package com.testehan.deepresearch.model;

public record GroundingSearchRequest(
        String subject,
        String modelId,
        Integer maxSources,
        String discoveryPrompt
) {
    public int resolvedMaxSources() {
        return (maxSources != null && maxSources > 0) ? maxSources : ResearchRequest.DEFAULT_MAX_SOURCES;
    }

    public String resolvedModelId() {
        return (modelId != null && !modelId.isBlank()) ? modelId : "ollama";
    }

    public String resolvedDiscoveryPrompt() {
        return discoveryPrompt != null ? discoveryPrompt : ResearchRequest.DEFAULT_DISCOVERY_PROMPT;
    }
}
