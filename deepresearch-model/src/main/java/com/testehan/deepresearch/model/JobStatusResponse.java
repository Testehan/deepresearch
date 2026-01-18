package com.testehan.deepresearch.model;

public record JobStatusResponse(
        String status,
        ResearchJob.JobResult result,
        String filePath,
        String errorMessage
) {
    public JobStatusResponse(String status, ResearchJob.JobResult result, String filePath) {
        this(status, result, filePath, null);
    }
}