package com.testehan.deepresearch.controller;

import com.testehan.deepresearch.model.ResearchJob;
import com.testehan.deepresearch.model.ResearchRequest;
import com.testehan.deepresearch.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResearchController {

    private final JobService jobService;

    public ResearchController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/api/research")
    public ResponseEntity<JobResponse> createResearch(@RequestBody ResearchRequest request) {
        if (request.topic() == null || request.topic().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var job = jobService.createJob(request);
        jobService.executeJob(job.jobId());
        return ResponseEntity.accepted().body(new JobResponse(job.jobId(), job.topic(), job.status().name()));
    }

    @GetMapping("/api/research/{jobId}")
    public ResponseEntity<?> getResearch(@PathVariable String jobId) {
        var job = jobService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.status() == ResearchJob.JobStatus.COMPLETED) {
            return ResponseEntity.ok(new JobStatusResponse(
                    job.status().name(),
                    job.result(),
                    job.filePath()
            ));
        } else if (job.status() == ResearchJob.JobStatus.FAILED) {
            return ResponseEntity.ok(new JobStatusResponse(
                    job.status().name(),
                    null,
                    null,
                    job.errorMessage()
            ));
        } else {
            return ResponseEntity.ok(new JobStatusResponse(job.status().name(), null, null));
        }
    }

    public record JobResponse(String jobId, String topic, String status) {}

    public record JobStatusResponse(
            String status,
            ResearchJob.JobResult result,
            String filePath,
            String errorMessage
    ) {
        public JobStatusResponse(String status, ResearchJob.JobResult result, String filePath) {
            this(status, result, filePath, null);
        }

        public JobStatusResponse(String status, ResearchJob.JobResult result, String filePath, String errorMessage) {
            this.status = status;
            this.result = result;
            this.filePath = filePath;
            this.errorMessage = errorMessage;
        }
    }
}