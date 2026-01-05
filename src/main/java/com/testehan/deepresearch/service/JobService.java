package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.Diagnostics;
import com.testehan.deepresearch.model.ResearchJob;
import com.testehan.deepresearch.model.ResearchReport;
import com.testehan.deepresearch.model.SourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final Map<String, ResearchJob> jobs = new ConcurrentHashMap<>();
    private final ResearchPipeline pipeline;

    public JobService(ResearchPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public ResearchJob createJob(String topic) {
        String jobId = UUID.randomUUID().toString();
        var job = new ResearchJob(
                jobId,
                topic,
                ResearchJob.JobStatus.PENDING,
                null,
                null,
                Instant.now(),
                null,
                null
        );
        jobs.put(jobId, job);
        log.info("Created job {} for topic: {}", jobId, topic);
        return job;
    }

    public ResearchJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    @Async
    public void executeJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            log.error("Job {} not found", jobId);
            return;
        }

        jobs.put(jobId, new ResearchJob(
                jobId,
                job.topic(),
                ResearchJob.JobStatus.RUNNING,
                null,
                null,
                job.createdAt(),
                null,
                null
        ));
        log.info("Starting execution for job {}", jobId);

        try {
            ResearchReport report = pipeline.execute(job.topic());
            
            var result = new ResearchJob.JobResult(
                    report.executiveSummary(),
                    report.keyFindings(),
                    report.themes(),
                    report.openQuestions(),
                    report.sources().stream()
                            .map(s -> new SourceReference(s.url(), s.title()))
                            .toList(),
                    new Diagnostics(
                            report.diagnostics().queriesGenerated(),
                            report.diagnostics().urlsDiscovered(),
                            report.diagnostics().urlsFetched(),
                            report.diagnostics().sourcesUsed(),
                            report.diagnostics().durationMs()
                    )
            );

            String filePath = findReportFile(job.topic());

            jobs.put(jobId, new ResearchJob(
                    jobId,
                    job.topic(),
                    ResearchJob.JobStatus.COMPLETED,
                    null,
                    filePath,
                    job.createdAt(),
                    Instant.now(),
                    result
            ));
            log.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            jobs.put(jobId, new ResearchJob(
                    jobId,
                    job.topic(),
                    ResearchJob.JobStatus.FAILED,
                    e.getMessage(),
                    null,
                    job.createdAt(),
                    Instant.now(),
                    null
            ));
        }
    }

    private String findReportFile(String topic) {
        try {
            var dir = Path.of("reports");
            String slug = topic.toLowerCase().replaceAll("[^a-z0-9]+", "-");
            var files = Files.list(dir)
                    .filter(f -> f.getFileName().toString().contains(slug))
                    .toList();
            return files.isEmpty() ? null : files.get(0).toString();
        } catch (IOException e) {
            log.warn("Could not find report file: {}", e.getMessage());
            return null;
        }
    }
}