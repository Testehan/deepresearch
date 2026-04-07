package com.testehan.deepresearch.model;

public record JobStatusResponse(
        String status,
        ReportResult result,
        String filePath,
        String errorMessage,
        LlmUsage llmUsage
) {
    public JobStatusResponse(String status, ReportResult result, String filePath) {
        this(status, result, filePath, null, null);
    }

    public JobStatusResponse(String status, ReportResult result, String filePath, String errorMessage) {
        this(status, result, filePath, errorMessage, null);
    }
}