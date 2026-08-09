package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.*;
import com.testehan.deepresearch.pipeline.BatchGeminiService;
import com.testehan.deepresearch.pipeline.DocumentProcessingService;
import com.testehan.deepresearch.pipeline.SynthesisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private ResearchPipeline pipeline;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private SynthesisService synthesisService;

    @Mock
    private BatchGeminiService batchGeminiService;

    @Mock
    private LlmCostService llmCostService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(pipeline, documentProcessingService, synthesisService, batchGeminiService, llmCostService);
    }

    @Test
    void createJob_shouldReturnPendingJob() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", null, null, null, null, null, null);

        var job = jobService.createJob(request);

        assertNotNull(job.jobId());
        assertEquals(ResearchTopic.NEWS, job.topic());
        assertEquals(ResearchJob.JobStatus.PENDING, job.status());
        assertNotNull(job.createdAt());
        assertNull(job.completedAt());
        assertNull(job.result());
    }

    @Test
    void getJob_shouldReturnJob() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", null, null, null, null, null, null);
        var createdJob = jobService.createJob(request);

        var retrievedJob = jobService.getJob(createdJob.jobId());

        assertEquals(createdJob.jobId(), retrievedJob.jobId());
        assertEquals(createdJob.topic(), retrievedJob.topic());
    }

    @Test
    void getJob_shouldReturnNullForUnknownId() {
        var job = jobService.getJob("unknown-id");
        assertNull(job);
    }

    @Test
    void executeJob_shouldHandleFailure() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", null, null, null, null, null, null);
        var job = jobService.createJob(request);

        when(pipeline.execute(any(), any(LlmUsage.class))).thenThrow(new RuntimeException("Pipeline failed"));

        jobService.executeJob(job.jobId());

        var failedJob = jobService.getJob(job.jobId());
        assertEquals(ResearchJob.JobStatus.FAILED, failedJob.status());
        assertEquals("Pipeline failed", failedJob.errorMessage());
    }

    @Test
    void executeJob_shouldCompleteSuccessfully() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", null, 5, null, null, null, null);
        var job = jobService.createJob(request);

        var mockReport = new NewsReport(
                ResearchTopic.NEWS,
                "Executive summary",
                List.of("Finding 1"),
                List.of("Theme 1"),
                List.of("Question 1"),
                List.of(new SourceReference("http://url", "Title")),
                new Diagnostics(2, 10, 8, 8, 1000)
        );
        when(pipeline.execute(any(), any(LlmUsage.class))).thenReturn(mockReport);

        jobService.executeJob(job.jobId());

        var completedJob = jobService.getJob(job.jobId());
        assertEquals(ResearchJob.JobStatus.COMPLETED, completedJob.status());
        assertNotNull(completedJob.result());
        assertTrue(completedJob.result() instanceof NewsReport);
        assertEquals("Executive summary", ((NewsReport)completedJob.result()).executiveSummary());
    }

    @Test
    void executeDocumentJob_shouldCompleteWithoutPersistingImagePath() throws Exception {
        var request = new ResearchDocumentRequest(ResearchTopic.EARNINGS_PRESENTATION, null, null);
        var job = jobService.createDocumentJob("presentation.pdf", request);
        var pdf = new MockMultipartFile("pdf", "presentation.pdf", "application/pdf", new byte[]{1, 2, 3});
        var images = List.of(new byte[]{4, 5, 6});
        var mockReport = new EarningsPresentationReport(
                null, null, null, List.of(), null, null, null, null, null, List.of("Highlight")
        );

        when(documentProcessingService.convertPdfToImages(pdf)).thenReturn(images);
        when(synthesisService.synthesizeDocument(any(), any(), any(), any(LlmUsage.class))).thenReturn(mockReport);

        jobService.executeDocumentJob(job.jobId(), pdf);

        var completedJob = jobService.getJob(job.jobId());
        assertEquals(ResearchJob.JobStatus.COMPLETED, completedJob.status());
        assertNull(completedJob.filePath());
        assertEquals(mockReport, completedJob.result());
        verify(synthesisService).synthesizeDocument(
                images,
                ResearchDocumentRequest.DEFAULT_PAGE_PROMPT,
                ResearchDocumentRequest.DEFAULT_COMPILE_REPORT_PROMPT,
                completedJob.llmUsage()
        );
    }

    @Test
    void batchPrompts_shouldMapReorderedResultsBySectionIdMetadata() {
        var request = new BatchPromptsRequest(
                "batch-gemini-2.5-pro",
                List.of(
                        new BatchPromptItem("darwinPricingPowerCustomerCaptivity:DARWIN", "pricing prompt"),
                        new BatchPromptItem("darwinTurnaroundMirage:DARWIN", "turnaround prompt")
                ),
                "caller-job"
        );
        var job = jobService.createBatchPromptsJob(request);

        when(batchGeminiService.submitBatchPrompts(eq(request.modelId()), eq(request.prompts())))
                .thenReturn("gemini-batch-1");
        when(batchGeminiService.isComplete("gemini-batch-1")).thenReturn(true);
        when(batchGeminiService.isSucceeded("gemini-batch-1")).thenReturn(true);
        when(batchGeminiService.retrieveBatchPromptResults("gemini-batch-1")).thenReturn(List.of(
                batchResult("darwinTurnaroundMirage:DARWIN", "turnaround result"),
                batchResult("darwinPricingPowerCustomerCaptivity:DARWIN", "pricing result")
        ));

        jobService.executeBatchPromptsJob(job.jobId());
        jobService.checkBatchJobs();

        var completedJob = jobService.getJob(job.jobId());
        assertEquals(ResearchJob.JobStatus.COMPLETED, completedJob.status());
        BatchPromptsResult result = (BatchPromptsResult) completedJob.result();
        assertEquals("darwinTurnaroundMirage:DARWIN", result.results().get(0).sectionId());
        assertEquals("turnaround result", result.results().get(0).text());
        assertEquals("darwinPricingPowerCustomerCaptivity:DARWIN", result.results().get(1).sectionId());
        assertEquals("pricing result", result.results().get(1).text());
    }

    @Test
    void batchPrompts_shouldFailWhenResultSectionIdIsDuplicated() {
        assertBatchPromptValidationFails(List.of(
                batchResult("section-a", "first"),
                batchResult("section-a", "duplicate")
        ), "duplicate section id");
    }

    @Test
    void batchPrompts_shouldFailWhenResultSectionIdIsUnknown() {
        assertBatchPromptValidationFails(List.of(
                batchResult("section-a", "first"),
                batchResult("section-c", "unknown")
        ), "unknown section id");
    }

    @Test
    void batchPrompts_shouldFailWhenResultSectionIdIsMissing() {
        assertBatchPromptValidationFails(List.of(
                batchResult("section-a", "first"),
                batchResult(null, "missing")
        ), "missing section id");
    }

    @Test
    void batchPrompts_shouldFailWhenResultCountDoesNotMatchSubmittedPromptCount() {
        assertBatchPromptValidationFails(List.of(
                batchResult("section-a", "first")
        ), "does not match prompt count");
    }

    private void assertBatchPromptValidationFails(List<BatchGeminiService.BatchResult> batchResults,
                                                  String expectedMessage) {
        var request = new BatchPromptsRequest(
                "batch-gemini-2.5-pro",
                List.of(
                        new BatchPromptItem("section-a", "prompt-a"),
                        new BatchPromptItem("section-b", "prompt-b")
                ),
                "caller-job"
        );
        var job = jobService.createBatchPromptsJob(request);

        when(batchGeminiService.submitBatchPrompts(eq(request.modelId()), eq(request.prompts())))
                .thenReturn("gemini-batch-1");
        when(batchGeminiService.isComplete("gemini-batch-1")).thenReturn(true);
        when(batchGeminiService.isSucceeded("gemini-batch-1")).thenReturn(true);
        when(batchGeminiService.retrieveBatchPromptResults("gemini-batch-1")).thenReturn(batchResults);

        jobService.executeBatchPromptsJob(job.jobId());
        jobService.checkBatchJobs();

        var failedJob = jobService.getJob(job.jobId());
        assertEquals(ResearchJob.JobStatus.FAILED, failedJob.status());
        assertTrue(failedJob.errorMessage().contains(expectedMessage));
    }

    private static BatchGeminiService.BatchResult batchResult(String sectionId, String text) {
        return new BatchGeminiService.BatchResult(sectionId, text, 1, 2, 0, 0, 0, 3);
    }
}
