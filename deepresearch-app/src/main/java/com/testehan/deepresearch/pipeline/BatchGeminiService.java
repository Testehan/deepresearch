package com.testehan.deepresearch.pipeline;

import com.google.genai.Client;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.InlinedRequest;
import com.google.genai.types.InlinedResponse;
import com.google.genai.types.JobState;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BatchGeminiService {

    private static final Logger log = LoggerFactory.getLogger(BatchGeminiService.class);

    private final String apiKey;

    BatchGeminiService(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /** Holds the text output and token counts for a single response inside a completed batch. */
    public record BatchResult(
            String text,
            int promptTokens,
            int completionTokens,
            int cachedTokens,
            int thoughtsTokens,
            int toolUsePromptTokens,
            int totalTokens) {}

    /**
     * Submits prompts as a Gemini Batch API job.
     *
     * @param modelId  the modelId from the request (e.g. "batch-gemini-2.5-pro")
     * @param prompts  ordered list of text prompts
     * @return the Gemini batch job name (used for polling and retrieval)
     */
    public String submitBatch(String modelId, List<String> prompts) {
        String model = stripBatchPrefix(modelId);
        log.info("Submitting batch of {} prompts using model {}", prompts.size(), model);

        List<InlinedRequest> requests = prompts.stream()
                .map(prompt -> InlinedRequest.builder()
                        .model(model)
                        .contents(List.of(Content.builder()
                                .role("user")
                                .parts(List.of(Part.fromText(prompt)))
                                .build()))
                        .build())
                .toList();

        BatchJobSource source = BatchJobSource.builder()
                .inlinedRequests(requests)
                .build();

        try (Client client = Client.builder().apiKey(apiKey).build()) {
            BatchJob job = client.batches.create("models/" + model, source, null);
            String jobName = job.name().orElseThrow(() ->
                    new IllegalStateException("Gemini batch job returned no name"));
            log.info("Batch job submitted: {}", jobName);
            return jobName;
        }
    }

    /**
     * @return true if the batch has reached a terminal state (succeeded, failed, cancelled, expired)
     */
    public boolean isComplete(String batchJobName) {
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            BatchJob job = client.batches.get(batchJobName, null);
            JobState.Known state = job.state().map(JobState::knownEnum).orElse(JobState.Known.JOB_STATE_UNSPECIFIED);
            log.debug("Batch job {} state: {}", batchJobName, state);
            return state == JobState.Known.JOB_STATE_SUCCEEDED
                    || state == JobState.Known.JOB_STATE_FAILED
                    || state == JobState.Known.JOB_STATE_CANCELLED
                    || state == JobState.Known.JOB_STATE_EXPIRED;
        }
    }

    /**
     * @return true if the batch succeeded
     */
    public boolean isSucceeded(String batchJobName) {
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            BatchJob job = client.batches.get(batchJobName, null);
            JobState.Known state = job.state().map(JobState::knownEnum).orElse(JobState.Known.JOB_STATE_UNSPECIFIED);
            return state == JobState.Known.JOB_STATE_SUCCEEDED;
        }
    }

    /**
     * Retrieves all results from a completed batch job, including token usage per response.
     * Preserves the original request order.
     */
    public List<BatchResult> retrieveResults(String batchJobName) {
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            BatchJob job = client.batches.get(batchJobName, null);
            List<InlinedResponse> responses = job.dest()
                    .flatMap(d -> d.inlinedResponses())
                    .orElseThrow(() -> new IllegalStateException("No inlined responses in batch job " + batchJobName));

            return responses.stream()
                    .map(this::toBatchResult)
                    .toList();
        }
    }

    private BatchResult toBatchResult(InlinedResponse r) {
        String text = r.response()
                .flatMap(resp -> resp.candidates())
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0))
                .flatMap(c -> c.content())
                .flatMap(c -> c.parts())
                .filter(p -> !p.isEmpty())
                .map(p -> p.get(0))
                .flatMap(Part::text)
                .orElse("");

        int prompt     = 0;
        int completion = 0;
        int cached     = 0;
        int thoughts   = 0;
        int toolUsePrompt = 0;
        int total      = 0;

        var usageMeta = r.response()
                .flatMap(resp -> resp.usageMetadata());
        if (usageMeta.isPresent()) {
            GenerateContentResponseUsageMetadata u = usageMeta.get();
            prompt     = u.promptTokenCount().orElse(0);
            completion = u.candidatesTokenCount().orElse(0);
            cached     = u.cachedContentTokenCount().orElse(0);
            thoughts   = u.thoughtsTokenCount().orElse(0);
            toolUsePrompt = u.toolUsePromptTokenCount().orElse(0);
            total      = u.totalTokenCount().orElse(0);
        }
        if (total == 0) {
            total = prompt + completion + thoughts + toolUsePrompt;
        }

        return new BatchResult(text, prompt, completion, cached, thoughts, toolUsePrompt, total);
    }

    private static String stripBatchPrefix(String modelId) {
        return modelId.startsWith("batch-") ? modelId.substring(6) : modelId;
    }
}
