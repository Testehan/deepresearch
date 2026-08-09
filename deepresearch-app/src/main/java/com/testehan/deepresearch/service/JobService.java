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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public ResearchJob<BatchPromptsRequest> createBatchPromptsJob(BatchPromptsRequest request) {
        String jobId = UUID.randomUUID().toString();
        var job = new ResearchJob<>(
                jobId, ResearchTopic.BATCH_PROMPTS, ResearchJob.JobStatus.PENDING,
                null, null, Instant.now(), null, null, null, request, null, null
        );
        jobs.put(jobId, job);
        log.info("Created batch-prompts job {} with {} prompts (model: {}, callerJobId: {})",
                jobId, request.prompts().size(), request.modelId(), request.callerJobId());
        return job;
    }

    public String submitBatchPrompts(BatchPromptsRequest request) {
        String geminiBatchJobId = batchGeminiService.submitBatchPrompts(request.modelId(), request.prompts());
        log.info("Batch-prompts request submitted to Gemini batch (batchJobId={}, callerJobId={})",
                geminiBatchJobId, request.callerJobId());
        return geminiBatchJobId;
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

    @Async
    @SuppressWarnings("unchecked")
    public void executeBatchPromptsJob(String jobId) {
        var job = (ResearchJob<BatchPromptsRequest>) jobs.get(jobId);
        if (job == null) {
            log.error("Batch-prompts job {} not found", jobId);
            return;
        }

        jobs.put(jobId, withStatus(job, ResearchJob.JobStatus.RUNNING));
        log.info("Starting execution for batch-prompts job {}", jobId);

        try {
            BatchPromptsRequest request = job.config();
            String geminiBatchJobId = batchGeminiService.submitBatchPrompts(request.modelId(), request.prompts());

            jobs.put(jobId, new ResearchJob<>(
                    jobId, job.topic(), ResearchJob.JobStatus.BATCH_POLLING,
                    null, null, job.createdAt(), null, null, new LlmUsage(), request,
                    geminiBatchJobId, null
            ));
            log.info("Batch-prompts job {} submitted to Gemini batch, polling started (batchJobId={})",
                    jobId, geminiBatchJobId);

        } catch (Exception e) {
            log.error("Batch-prompts job {} failed: {}", jobId, e.getMessage(), e);
            jobs.put(jobId, new ResearchJob<>(
                    jobId, job.topic(), ResearchJob.JobStatus.FAILED,
                    e.getMessage(), null, job.createdAt(), Instant.now(),
                    null, null, job.config(), null, null
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
            if (rawJob.config() instanceof BatchPromptsRequest) {
                pollBatchPromptsJob((ResearchJob<BatchPromptsRequest>) (ResearchJob<?>) rawJob);
            } else {
                pollNewsBatchJob((ResearchJob<ResearchRequest>) (ResearchJob<?>) rawJob);
            }
        }
    }

    private void pollNewsBatchJob(ResearchJob<ResearchRequest> job) {
        String batchJobId = job.batchJobId();
        log.debug("Polling Gemini batch job {} for research job {}", batchJobId, job.jobId());

        try {
            if (!batchGeminiService.isComplete(batchJobId)) return;

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

    private void pollBatchPromptsJob(ResearchJob<BatchPromptsRequest> job) {
        String batchJobId = job.batchJobId();
        log.debug("Polling Gemini batch job {} for batch-prompts job {}", batchJobId, job.jobId());

        try {
            if (!batchGeminiService.isComplete(batchJobId)) return;

            BatchPromptsRequest request = job.config();
            LlmUsage usage = job.llmUsage() != null ? job.llmUsage() : new LlmUsage();

            if (batchGeminiService.isSucceeded(batchJobId)) {
                List<BatchGeminiService.BatchResult> batchResults = batchGeminiService.retrieveBatchPromptResults(batchJobId);
                llmCostService.logAndAccumulateBatch(batchResults, request.modelId(), usage);

                List<BatchPromptItem> items = request.prompts();
                validateBatchPromptResultIds(job.jobId(), items, batchResults);

                List<BatchPromptResult> mapped = new ArrayList<>(batchResults.size());
                for (BatchGeminiService.BatchResult r : batchResults) {
                    mapped.add(new BatchPromptResult(
                            r.sectionId(),
                            r.text(),
                            r.promptTokens(),
                            r.completionTokens(),
                            r.cachedTokens(),
                            r.thoughtsTokens(),
                            r.toolUsePromptTokens(),
                            r.totalTokens()
                    ));
                }
                BatchPromptsResult result = new BatchPromptsResult(mapped);

                jobs.put(job.jobId(), new ResearchJob<>(
                        job.jobId(), job.topic(), ResearchJob.JobStatus.COMPLETED,
                        null, null, job.createdAt(), Instant.now(), result, usage, request, batchJobId, null
                ));
                log.info("Batch-prompts job {} completed ({} results)", job.jobId(), mapped.size());
            } else {
                jobs.put(job.jobId(), new ResearchJob<>(
                        job.jobId(), job.topic(), ResearchJob.JobStatus.FAILED,
                        "Gemini batch job " + batchJobId + " did not succeed",
                        null, job.createdAt(), Instant.now(), null, null, request, batchJobId, null
                ));
                log.warn("Batch-prompts job {} failed (Gemini batch {})", job.jobId(), batchJobId);
            }
        } catch (Exception e) {
            log.error("Error polling Gemini batch {} for batch-prompts job {}: {}",
                    batchJobId, job.jobId(), e.getMessage(), e);
            jobs.put(job.jobId(), new ResearchJob<>(
                    job.jobId(), job.topic(), ResearchJob.JobStatus.FAILED,
                    e.getMessage(), null, job.createdAt(), Instant.now(),
                    null, job.llmUsage(), job.config(), batchJobId, null
            ));
        }
    }

    public JobStatusResponse getBatchPromptsStatus(String batchJobId, String modelId) {
        if (batchJobId == null || batchJobId.isBlank()) {
            throw new IllegalArgumentException("batchJobId is required");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId is required");
        }

        if (!batchGeminiService.isComplete(batchJobId)) {
            return new JobStatusResponse(
                    ResearchJob.JobStatus.BATCH_POLLING.toString(),
                    null,
                    null,
                    null,
                    null,
                    batchJobId
            );
        }

        if (!batchGeminiService.isSucceeded(batchJobId)) {
            return new JobStatusResponse(
                    ResearchJob.JobStatus.FAILED.toString(),
                    null,
                    null,
                    "Gemini batch job " + batchJobId + " did not succeed",
                    null,
                    batchJobId
            );
        }

        List<BatchGeminiService.BatchResult> batchResults = batchGeminiService.retrieveBatchPromptResults(batchJobId);
        LlmUsage usage = new LlmUsage();
        llmCostService.logAndAccumulateBatch(batchResults, modelId, usage);

        List<BatchPromptResult> mapped = batchResults.stream()
                .map(r -> new BatchPromptResult(
                        r.sectionId(),
                        r.text(),
                        r.promptTokens(),
                        r.completionTokens(),
                        r.cachedTokens(),
                        r.thoughtsTokens(),
                        r.toolUsePromptTokens(),
                        r.totalTokens()
                ))
                .toList();

        return new JobStatusResponse(
                ResearchJob.JobStatus.COMPLETED.toString(),
                new BatchPromptsResult(mapped),
                null,
                null,
                usage,
                batchJobId
        );
    }

    private void validateBatchPromptResultIds(String jobId,
                                              List<BatchPromptItem> submitted,
                                              List<BatchGeminiService.BatchResult> results) {
        Set<String> submittedIds = submitted.stream()
                .map(BatchPromptItem::sectionId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (submittedIds.size() != submitted.size()) {
            throw new IllegalStateException("Batch prompt job " + jobId + " contains duplicate submitted section ids");
        }
        if (results.size() != submitted.size()) {
            throw new IllegalStateException("Batch result count " + results.size()
                    + " does not match prompt count " + submitted.size()
                    + " for job " + jobId);
        }

        Set<String> resultIds = new HashSet<>();
        for (BatchGeminiService.BatchResult result : results) {
            String sectionId = result.sectionId();
            if (sectionId == null || sectionId.isBlank()) {
                throw new IllegalStateException("Batch result missing section id for job " + jobId);
            }
            if (!submittedIds.contains(sectionId)) {
                throw new IllegalStateException("Batch result contains unknown section id '" + sectionId
                        + "' for job " + jobId);
            }
            if (!resultIds.add(sectionId)) {
                throw new IllegalStateException("Batch result contains duplicate section id '" + sectionId
                        + "' for job " + jobId);
            }
        }

        Set<String> missingIds = new LinkedHashSet<>(submittedIds);
        missingIds.removeAll(resultIds);
        if (!missingIds.isEmpty()) {
            throw new IllegalStateException("Batch result missing section ids " + missingIds + " for job " + jobId);
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
