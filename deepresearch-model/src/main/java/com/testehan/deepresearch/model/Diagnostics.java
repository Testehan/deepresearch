package com.testehan.deepresearch.model;

public record Diagnostics(
        int queriesGenerated,
        int urlsDiscovered,
        int urlsFetched,
        int sourcesUsed,
        long durationMs
) {}