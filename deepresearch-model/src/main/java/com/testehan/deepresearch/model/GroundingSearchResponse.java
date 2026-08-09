package com.testehan.deepresearch.model;

import java.util.List;

public record GroundingSearchResponse(
        List<String> queries,
        List<SearchCandidate> candidates,
        List<FetchedSource> sources,
        LlmUsage llmUsage
) {}
