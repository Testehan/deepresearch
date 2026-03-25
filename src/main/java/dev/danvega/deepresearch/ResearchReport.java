package dev.danvega.deepresearch;

import java.util.List;

public record ResearchReport(
        String topic,
        String executiveSummary,
        List<String> keyFindings,
        List<String> themes,
        List<String> openQuestions,
        List<SourceReference> sources,
        Diagnostics diagnostics
) {
    public record SourceReference(String url, String title) {
    }

    public record Diagnostics(
            int queriesGenerated,
            int urlsDiscovered,
            int urlsFetched,
            int sourcesUsed,
            long durationMs
    ) {
    }
}
