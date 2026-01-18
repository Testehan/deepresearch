package com.testehan.deepresearch.model;

import java.time.Instant;
import java.util.List;

public record ResearchJob<T>(
        String jobId,
        ResearchTopic topic,
        JobStatus status,
        String errorMessage,
        String filePath,
        Instant createdAt,
        Instant completedAt,
        ReportResult result,
        T config
) {
    public enum JobStatus {
        PENDING("pending"),
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String value;

        JobStatus(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static JobStatus fromValue(String value) {
            for (JobStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }
}