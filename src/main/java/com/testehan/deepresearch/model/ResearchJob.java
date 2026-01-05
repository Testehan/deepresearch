package com.testehan.deepresearch.model;

import java.time.Instant;
import java.util.List;

public record ResearchJob(
        String jobId,
        String topic,
        JobStatus status,
        String errorMessage,
        String filePath,
        Instant createdAt,
        Instant completedAt,
        JobResult result
) {
    public record JobResult(
            String executiveSummary,
            List<String> keyFindings,
            List<String> themes,
            List<String> openQuestions,
            List<SourceReference> sources,
            Diagnostics diagnostics
    ) {}

    public enum JobStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}