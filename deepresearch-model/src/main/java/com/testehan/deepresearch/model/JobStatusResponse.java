package com.testehan.deepresearch.model;

public record JobStatusResponse(
        String status,
        ReportResult result,
        String filePath,
        String errorMessage
) {
    public JobStatusResponse(String status, ReportResult result, String filePath) {
        this(status, result, filePath, null);
    }
}