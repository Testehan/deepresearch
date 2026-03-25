package dev.danvega.deepresearch;

import dev.danvega.browserbase.Browserbase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final Browserbase browserbase;
    private final ChatClient chatClient;

    DiscoveryService(Browserbase browserbase, ChatClient.Builder builder) {
        this.browserbase = browserbase;
        this.chatClient = builder.build();
    }

    List<SearchCandidate> discover(String topic) {
        log.info("--- Step 1: Search + Discover ---");

        // Generate diverse search queries using the LLM
        List<String> queries = chatClient.prompt("""
                Generate 5 diverse search queries to thoroughly research this topic: "%s"

                Vary the phrasing and angle. Include queries for:
                - The topic itself
                - Latest developments
                - Current year trends
                - Benchmarks or comparisons
                - Case studies or real-world examples

                Return only a JSON array of strings, nothing else.
                """.formatted(topic))
                .call()
                .entity(new ParameterizedTypeReference<>() {});

        // Execute each query and collect results
        int rawHits = 0;
        Set<String> seenUrls = new LinkedHashSet<>();
        List<SearchCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            var results = browserbase.search().web(query, 15).results();
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

        return candidates;
    }
}
