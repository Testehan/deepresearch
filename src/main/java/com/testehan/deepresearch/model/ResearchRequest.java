package com.testehan.deepresearch.model;

public record ResearchRequest(
    String topic,
    Integer maxSources,
    Integer chunkSize,
    String discoveryPrompt,
    String synthesisPrompt
) {
    public static final int DEFAULT_MAX_SOURCES = 15;
    public static final int DEFAULT_CHUNK_SIZE = 4;

    public static final String DEFAULT_DISCOVERY_PROMPT = """
            Generate %d diverse search queries to thoroughly research this topic: "%s"

            Vary the phrasing and angle. Include queries for:
            - The topic itself
            - Latest developments
            - Current year trends
            - Benchmarks or comparisons
            - Case studies or real-world examples

            Return only a JSON array of strings, nothing else.
            """;

    public static final String DEFAULT_ANALYZE_CHUNK_PROMPT = """
            You are a research analyst. Analyze these sources about "%s".

            Extract and summarize:
            - Key facts and findings (cite the source URL for each)
            - Points where sources agree
            - Points where sources disagree or have caveats

            Sources:
            %s
            """;

    public static final String DEFAULT_COMPILE_REPORT_PROMPT = """
            You are a senior research analyst compiling a briefing document about "%s".

            Based on these extracted findings from multiple sources, produce:
            1. An executive summary (2-3 paragraphs)
            2. Key findings (list of concise bullet points, cite source URLs)
            3. Major themes and areas of consensus
            4. Open questions that need further research

            Extracted findings:
            %s
            """;

    public int resolvedMaxSources() {
        return maxSources != null ? maxSources : DEFAULT_MAX_SOURCES;
    }

    public int resolvedChunkSize() {
        return chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE;
    }

    public String resolvedDiscoveryPrompt() {
        return discoveryPrompt != null ? discoveryPrompt : DEFAULT_DISCOVERY_PROMPT;
    }

    public String resolvedSynthesisPrompt() {
        return synthesisPrompt != null ? synthesisPrompt : DEFAULT_ANALYZE_CHUNK_PROMPT;
    }

    public String compileReportPrompt() {
        return DEFAULT_COMPILE_REPORT_PROMPT;
    }
}