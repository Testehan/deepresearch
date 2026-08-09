package com.testehan.deepresearch.controller;

import com.testehan.deepresearch.model.BatchPromptsRequest;
import com.testehan.deepresearch.model.JobStatusResponse;
import com.testehan.deepresearch.model.JobResponse;
import com.testehan.deepresearch.model.ResearchTopic;
import com.testehan.deepresearch.service.JobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BatchPromptsController {

    private final JobService jobService;

    public BatchPromptsController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/api/batch-prompts")
    public ResponseEntity<JobResponse> createBatchPrompts(@RequestBody BatchPromptsRequest request) {
        if (request == null
                || request.prompts() == null
                || request.prompts().isEmpty()
                || !request.isBatchModel()) {
            return ResponseEntity.badRequest().build();
        }
        for (var item : request.prompts()) {
            if (item.sectionId() == null || item.sectionId().isBlank()
                    || item.prompt() == null || item.prompt().isBlank()) {
                return ResponseEntity.badRequest().build();
            }
        }

        String batchJobId = jobService.submitBatchPrompts(request);
        return ResponseEntity.accepted()
                .body(new JobResponse(null, ResearchTopic.BATCH_PROMPTS, "batch_polling", batchJobId));
    }

    @GetMapping("/api/batch-prompts/status")
    public ResponseEntity<JobStatusResponse> getBatchPromptsStatus(
            @RequestParam String batchJobId,
            @RequestParam String modelId) {
        return ResponseEntity.ok(jobService.getBatchPromptsStatus(batchJobId, modelId));
    }
}
