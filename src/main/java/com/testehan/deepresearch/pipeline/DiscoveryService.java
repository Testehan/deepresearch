package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.model.SearchCandidate;
import dev.danvega.browserbase.Browserbase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final Browserbase browserbase;
    private final ChatClient chatClient;
    private final int maxSources;

    DiscoveryService(Browserbase browserbase, ChatClient.Builder builder, @Value("${research.max-sources:15}") int maxSources) {
        this.browserbase = browserbase;
        this.chatClient = builder.build();
        this.maxSources = maxSources;
    }

    public record DiscoveryResult(List<SearchCandidate> candidates, int queriesGenerated) {}

    public DiscoveryResult discover(String topic) {
        log.info("--- Step 1: Search + Discover ---");

        int numQueries = (maxSources <= 5) ? 1 : 2;

        List<String> queries = chatClient.prompt("""
                Generate %d diverse search queries to thoroughly research this topic: "%s"

                Vary the phrasing and angle. Include queries for:
                - The topic itself
                - Latest developments
                - Current year trends
                - Benchmarks or comparisons
                - Case studies or real-world examples

                Return only a JSON array of strings, nothing else.
                """.formatted(numQueries, topic))
                .call()
                .entity(new ParameterizedTypeReference<>() {});

        int rawHits = 0;
        Set<String> seenUrls = new LinkedHashSet<>();
        List<SearchCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            var results = browserbase.search().web(query, maxSources).results();
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