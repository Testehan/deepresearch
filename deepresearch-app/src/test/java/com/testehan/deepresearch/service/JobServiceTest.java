package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.*;
import com.testehan.deepresearch.pipeline.DocumentProcessingService;
import com.testehan.deepresearch.pipeline.SynthesisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private ResearchPipeline pipeline;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private SynthesisService synthesisService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(pipeline, documentProcessingService, synthesisService);
    }

    @Test
    void createJob_shouldReturnPendingJob() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", null, null, null, null, null);

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
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", null, null, null, null, null);
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
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", null, null, null, null, null);
        var job = jobService.createJob(request);

        when(pipeline.execute(any())).thenThrow(new RuntimeException("Pipeline failed"));

        jobService.executeJob(job.jobId());

        var failedJob = jobService.getJob(job.jobId());
        assertEquals(ResearchJob.JobStatus.FAILED, failedJob.status());
        assertEquals("Pipeline failed", failedJob.errorMessage());
    }

    @Test
    void executeJob_shouldCompleteSuccessfully() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test topic", 5, null, null, null, null);
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
        when(pipeline.execute(any())).thenReturn(mockReport);

        jobService.executeJob(job.jobId());

        var completedJob = jobService.getJob(job.jobId());
        assertEquals(ResearchJob.JobStatus.COMPLETED, completedJob.status());
        assertNotNull(completedJob.result());
        assertTrue(completedJob.result() instanceof NewsReport);
        assertEquals("Executive summary", ((NewsReport)completedJob.result()).executiveSummary());
    }
}
