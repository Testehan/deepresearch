package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.config.ChatClientProvider;
import com.testehan.deepresearch.model.LlmUsage;
import com.testehan.deepresearch.model.SearchCandidate;
import com.testehan.deepresearch.service.LlmCostService;
import dev.danvega.browserbase.Browserbase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final Browserbase browserbase;
    private final ChatClientProvider chatClientProvider;
    private final LlmCostService llmCostService;
    private final int defaultMaxSources;

    DiscoveryService(Browserbase browserbase, ChatClientProvider chatClientProvider,
                     LlmCostService llmCostService,
                     @Value("${research.max-sources:15}") int defaultMaxSources) {
        this.browserbase = browserbase;
        this.chatClientProvider = chatClientProvider;
        this.llmCostService = llmCostService;
        this.defaultMaxSources = defaultMaxSources;
    }

    public record DiscoveryResult(List<SearchCandidate> candidates, int queriesGenerated) {}

    public DiscoveryResult discover(String topic, int maxSources, String discoveryPrompt,
                                    LlmUsage usage, String modelId) {
        log.info("--- Step 1: Search + Discover ---");

        int effectiveMaxSources = maxSources > 0 ? maxSources : defaultMaxSources;
        int numQueries = (effectiveMaxSources <= 5) ? 1 : 2;

        var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<String>>() {});
        String prompt = discoveryPrompt.formatted(numQueries, topic) + "\n\n" + converter.getFormat();

        ChatClient client = chatClientProvider.chatClientFor(modelId);
        GoogleGenAiChatOptions options = chatClientProvider.optionsFor(modelId);
        var requestSpec = client.prompt(prompt);
        if (options != null) requestSpec = requestSpec.options(options);

        var chatResponse = requestSpec.call().chatResponse();
        llmCostService.logAndAccumulate(chatResponse, "discovery", usage, modelId);
        List<String> queries = converter.convert(chatResponse.getResult().getOutput().getText());

        int rawHits = 0;
        Set<String> seenUrls = new LinkedHashSet<>();
        List<SearchCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            if (query.length() > 150) {
                log.warn("Query exceeds 150 chars ({}), truncating: {}", query.length(), query);
                query = query.substring(0, 150);
            }
            var results = browserbase.search().web(query, effectiveMaxSources).results();
            log.info("  Query {}/{}: {} — {} results", i + 1, queries.size(), query, results.size());
            rawHits += results.size();

            for (var result : results) {
                if (seenUrls.add(result.url())) {
                    candidates.add(new SearchCandidate(result.url(), result.title(), query));
                }
            }
        }

        log.info("Discovery summary: {} raw hits -> {} unique URLs -> {} fetch candidates",
                rawHits, seenUrls.size(), candidates.size());

        return new DiscoveryResult(candidates, queries.size());
    }
}
