package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.*;
import com.testehan.deepresearch.pipeline.DocumentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    private final Map<String, ResearchJob<?>> jobs = new ConcurrentHashMap<>();
    private final ResearchPipeline pipeline;
    private final DocumentProcessingService documentProcessingService;

    public JobService(ResearchPipeline pipeline, DocumentProcessingService documentProcessingService) {
        this.pipeline = pipeline;
        this.documentProcessingService = documentProcessingService;
    }

    public ResearchJob<ResearchRequest> createJob(ResearchRequest request) {
        String jobId = UUID.randomUUID().toString();
        var job = new ResearchJob<>(
                jobId,
                request.topic(),
                ResearchJob.JobStatus.PENDING,
                null,
                null,
                Instant.now(),
                null,
                null,
                request
        );
        jobs.put(jobId, job);
        log.info("Created job {} for topic: {}", jobId, request.topic());
        return job;
    }

    public ResearchJob<ResearchDocumentRequest> createDocumentJob(String filename, ResearchDocumentRequest request) {
        String jobId = UUID.randomUUID().toString();
        var job = new ResearchJob<>(
                jobId,
                filename,
                ResearchJob.JobStatus.PENDING,
                null,
                null,
                Instant.now(),
                null,
                null,
                request
        );
        jobs.put(jobId, job);
        log.info("Created document job {} for file: {}", jobId, filename);
        return job;
    }

    public ResearchJob<?> getJob(String jobId) {
        return jobs.get(jobId);
    }

    @Async
    @SuppressWarnings("unchecked")
    public void executeJob(String jobId) {
        var job = (ResearchJob<ResearchRequest>) jobs.get(jobId);
        if (job == null) {
            log.error("Job {} not found", jobId);
            return;
        }

        jobs.put(jobId, new ResearchJob<>(
                jobId,
                job.topic(),
                ResearchJob.JobStatus.RUNNING,
                null,
                null,
                job.createdAt(),
                null,
                null,
                job.config()
        ));
        log.info("Starting execution for job {}", jobId);

        try {
            ResearchRequest request = job.config();
            ResearchReport report = pipeline.execute(request);
            
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

            String filePath = findReportFile(request.topic());

            jobs.put(jobId, new ResearchJob<>(
                    jobId,
                    job.topic(),
                    ResearchJob.JobStatus.COMPLETED,
                    null,
                    filePath,
                    job.createdAt(),
                    Instant.now(),
                    result,
                    job.config()
            ));
            log.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            jobs.put(jobId, new ResearchJob<>(
                    jobId,
                    job.topic(),
                    ResearchJob.JobStatus.FAILED,
                    e.getMessage(),
                    null,
                    job.createdAt(),
                    Instant.now(),
                    null,
                    job.config()
            ));
        }
    }

    @Async
    @SuppressWarnings("unchecked")
    public void executeDocumentJob(String jobId, MultipartFile pdfFile) {
        var job = (ResearchJob<ResearchDocumentRequest>) jobs.get(jobId);
        if (job == null) {
            log.error("Document Job {} not found", jobId);
            return;
        }

        jobs.put(jobId, new ResearchJob<>(
                jobId,
                job.topic(),
                ResearchJob.JobStatus.RUNNING,
                null,
                null,
                job.createdAt(),
                null,
                null,
                job.config()
        ));
        log.info("Starting execution for document job {}", jobId);

        try {
            var images = documentProcessingService.convertPdfToImages(pdfFile);
            
            // Save images to disk
            Path jobDir = Path.of("reports", "images", jobId);
            Files.createDirectories(jobDir);
            for (int i = 0; i < images.size(); i++) {
                Path imagePath = jobDir.resolve("page-%d.png".formatted(i + 1));
                Files.write(imagePath, images.get(i));
            }
            log.info("Saved {} images to {}", images.size(), jobDir);

            // Placeholder for full document processing logic (OCR, synthesis, etc.)
            // For now, we just mark it as completed with minimal results
            var result = new ResearchJob.JobResult(
                    "Document processed successfully. Saved " + images.size() + " images.",
                    null, null, null, null, 
                    new Diagnostics(0, 0, 0, 0, 0)
            );

            jobs.put(jobId, new ResearchJob<>(
                    jobId,
                    job.topic(),
                    ResearchJob.JobStatus.COMPLETED,
                    null,
                    jobDir.toString(),
                    job.createdAt(),
                    Instant.now(),
                    result,
                    job.config()
            ));
            log.info("Document job {} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Document job {} failed: {}", jobId, e.getMessage(), e);
            jobs.put(jobId, new ResearchJob<>(
                    jobId,
                    job.topic(),
                    ResearchJob.JobStatus.FAILED,
                    e.getMessage(),
                    null,
                    job.createdAt(),
                    Instant.now(),
                    null,
                    job.config()
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