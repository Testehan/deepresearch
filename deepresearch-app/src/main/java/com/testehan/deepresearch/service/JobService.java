package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.*;
import com.testehan.deepresearch.pipeline.BatchGeminiService;
import com.testehan.deepresearch.pipeline.DocumentProcessingService;
import com.testehan.deepresearch.pipeline.SynthesisService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final Map<String, ResearchJob<?>> jobs = new ConcurrentHashMap<>();
    private final ResearchPipeline pipeline;
    private final DocumentProcessingService documentProcessingService;
    private final SynthesisService synthesisService;
    private final BatchGeminiService batchGeminiService;
    private final LlmCostService llmCostService;

    public JobService(ResearchPipeline pipeline,
                      DocumentProcessingService documentProcessingService,
                      SynthesisService synthesisService,
                      BatchGeminiService batchGeminiService,
                      LlmCostService llmCostService) {
        this.pipeline = pipeline;
        this.documentProcessingService = documentProcessingService;
        this.synthesisService = synthesisService;
        this.batchGeminiService = batchGeminiService;
        this.llmCostService = llmCostService;
    }

    public ResearchJob<ResearchRequest> createJob(ResearchRequest request) {
        String jobId = UUID.randomUUID().toString();
        var job = new ResearchJob<>(
                jobId, request.topic(), ResearchJob.JobStatus.PENDING,
                null, null, Instant.now(), null, null, null, request, null, null
        );
        jobs.put(jobId, job);
        log.info("Created job {} for subject: {} (model: {})", jobId, request.subject(), request.resolvedModelId());
        return job;
    }

    public ResearchJob<ResearchDocumentRequest> createDocumentJob(String filename, ResearchDocumentRequest request) {
        String jobId = UUID.randomUUID().toString();
        var job = new ResearchJob<>(
                jobId, request.topic(), ResearchJob.JobStatus.PENDING,
                null, null, Instant.now(), null, null, null, request, null, null
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

        jobs.put(jobId, withStatus(job, ResearchJob.JobStatus.RUNNING));
        log.info("Starting execution for job {}", jobId);

        try {
            ResearchRequest request = job.config();
            LlmUsage usage = new LlmUsage();

            if (request.isBatchModel()) {
                var submission = pipeline.executeForBatch(request, usage);
                jobs.put(jobId, new ResearchJob<>(
                        jobId, job.topic(), ResearchJob.JobStatus.BATCH_POLLING,
                        null, null, job.createdAt(), null, null, usage, request,
                        submission.batchJobId(), submission.sourceRefs()
                ));
                log.info("Job {} submitted to Gemini batch, polling started (batchJobId={})",
                        jobId, submission.batchJobId());
            } else {
                NewsReport report = pipeline.execute(request, usage);
                String filePath = findReportFile(request.subject());
                jobs.put(jobId, new ResearchJob<>(
                        jobId, job.topic(), ResearchJob.JobStatus.COMPLETED,
                        null, filePath, job.createdAt(), Instant.now(), report, usage, request, null, null
                ));
                log.info("Job {} completed successfully", jobId);
            }

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            jobs.put(jobId, new ResearchJob<>(
                    jobId, job.topic(), ResearchJob.JobStatus.FAILED,
                    e.getMessage(), null, job.createdAt(), Instant.now(), null, null, job.config(), null, null
            ));
        }
    }

    @Scheduled(fixedDelay = 30_000)
    @SuppressWarnings("unchecked")
    public void checkBatchJobs() {
        var batchJobs = jobs.values().stream()
                .filter(j -> j.status() == ResearchJob.JobStatus.BATCH_POLLING)
                .toList();

        for (var rawJob : batchJobs) {
            var job = (ResearchJob<ResearchRequest>) rawJob;
            String batchJobId = job.batchJobId();
            log.debug("Polling Gemini batch job {} for research job {}", batchJobId, job.jobId());

            try {
                if (!batchGeminiService.isComplete(batchJobId)) continue;

                ResearchRequest request = job.config();
                LlmUsage usage = job.llmUsage() != null ? job.llmUsage() : new LlmUsage();

                if (batchGeminiService.isSucceeded(batchJobId)) {
                    List<BatchGeminiService.BatchResult> batchResults = batchGeminiService.retrieveResults(batchJobId);
                    llmCostService.logAndAccumulateBatch(batchResults, request.resolvedModelId(), usage);

                    List<String> chunkSummaries = batchResults.stream()
                            .map(BatchGeminiService.BatchResult::text).toList();
                    List<SourceReference> sourceRefs = job.pendingSources() != null
                            ? job.pendingSources() : List.of();

                    NewsReport report = pipeline.completeBatchResult(request, chunkSummaries, sourceRefs, usage);
                    String filePath = findReportFile(request.subject());

                    jobs.put(job.jobId(), new ResearchJob<>(
                            job.jobId(), job.topic(), ResearchJob.JobStatus.COMPLETED,
                            null, filePath, job.createdAt(), Instant.now(), report, usage, request, batchJobId, null
                    ));
                    log.info("Batch job {} completed for research job {}", batchJobId, job.jobId());
                } else {
                    jobs.put(job.jobId(), new ResearchJob<>(
                            job.jobId(), job.topic(), ResearchJob.JobStatus.FAILED,
                            "Gemini batch job " + batchJobId + " did not succeed",
                            null, job.createdAt(), Instant.now(), null, null, request, batchJobId, null
                    ));
                    log.warn("Batch job {} failed for research job {}", batchJobId, job.jobId());
                }
            } catch (Exception e) {
                log.error("Error polling batch job {} for research job {}: {}",
                        batchJobId, job.jobId(), e.getMessage(), e);
            }
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

        jobs.put(jobId, withStatus(job, ResearchJob.JobStatus.RUNNING));
        log.info("Starting execution for document job {}", jobId);

        long start = System.currentTimeMillis();
        try {
            var images = documentProcessingService.convertPdfToImages(pdfFile);

            ResearchDocumentRequest config = job.config();
            LlmUsage usage = new LlmUsage();
            var synthesisReport = synthesisService.synthesizeDocument(
                    images,
                    config.resolvedPagePrompt(),
                    config.resolvedCompileReportPrompt(),
                    usage);

            long duration = System.currentTimeMillis() - start;
            log.info(" synthesisReport: {}", synthesisReport);

            var finalReport = new EarningsPresentationReport(
                    synthesisReport.companyMetadata(),
                    synthesisReport.headlineFinancials(),
                    synthesisReport.cashAndCapital(),
                    synthesisReport.industrySpecificKpis(),
                    synthesisReport.marketAndGrowthOpportunity(),
                    synthesisReport.revenueQualityAndRisk(),
                    synthesisReport.debtAndLeverage(),
                    synthesisReport.corporateActions(),
                    synthesisReport.forwardLookingGuidance(),
                    synthesisReport.strategicHighlights()
            );

            jobs.put(jobId, new ResearchJob<>(
                    jobId, job.topic(), ResearchJob.JobStatus.COMPLETED,
                    null, null, job.createdAt(), Instant.now(),
                    finalReport, usage, job.config(), null, null
            ));
            log.info("Document job {} completed successfully in {} ms", jobId, duration);

        } catch (Exception e) {
            log.error("Document job {} failed: {}", jobId, e.getMessage(), e);
            jobs.put(jobId, new ResearchJob<>(
                    jobId, job.topic(), ResearchJob.JobStatus.FAILED,
                    e.getMessage(), null, job.createdAt(), Instant.now(),
                    null, null, job.config(), null, null
            ));
        }
    }

    private static <T> ResearchJob<T> withStatus(ResearchJob<T> job, ResearchJob.JobStatus status) {
        return new ResearchJob<>(
                job.jobId(), job.topic(), status,
                null, null, job.createdAt(), null, null, null, job.config(), null, null
        );
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
