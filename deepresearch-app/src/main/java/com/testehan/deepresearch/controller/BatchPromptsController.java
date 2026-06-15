package com.testehan.deepresearch.controller;

import com.testehan.deepresearch.model.BatchPromptsRequest;
import com.testehan.deepresearch.model.JobResponse;
import com.testehan.deepresearch.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

        var job = jobService.createBatchPromptsJob(request);
        jobService.executeBatchPromptsJob(job.jobId());
        return ResponseEntity.accepted()
                .body(new JobResponse(job.jobId(), job.topic(), job.status().toString()));
    }
}
