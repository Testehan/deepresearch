package com.testehan.deepresearch.model;

public record JobResponse(String jobId, ResearchTopic topic, String status, String batchJobId) {
    public JobResponse(String jobId, ResearchTopic topic, String status) {
        this(jobId, topic, status, null);
    }
}
