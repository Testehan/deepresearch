package com.testehan.deepresearch.controller;

import com.testehan.deepresearch.model.BatchPromptItem;
import com.testehan.deepresearch.model.BatchPromptsRequest;
import com.testehan.deepresearch.model.JobResponse;
import com.testehan.deepresearch.model.ResearchTopic;
import com.testehan.deepresearch.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchPromptsControllerTest {

    @Mock
    private JobService jobService;

    @Test
    void createBatchPrompts_returnsOnlyGeminiBatchIdForStatelessBatchJobs() {
        BatchPromptsRequest request = new BatchPromptsRequest(
                "batch-gemini-2.5-pro",
                List.of(new BatchPromptItem("section-a", "prompt")),
                "caller-job"
        );
        when(jobService.submitBatchPrompts(request)).thenReturn("batches/gemini-batch-1");

        JobResponse response = new BatchPromptsController(jobService)
                .createBatchPrompts(request)
                .getBody();

        assertNull(response.jobId());
        assertEquals(ResearchTopic.BATCH_PROMPTS, response.topic());
        assertEquals("batch_polling", response.status());
        assertEquals("batches/gemini-batch-1", response.batchJobId());
    }
}
