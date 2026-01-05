package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.model.FetchedSource;
import com.testehan.deepresearch.model.SearchCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final PageFetcher pageFetcher;

    RetrievalService(PageFetcher pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    public List<FetchedSource> retrieve(List<SearchCandidate> candidates) {
        log.info("--- Step 2: Fetch ---");

        int total = candidates.size();
        var counter = new AtomicInteger(0);

        var sources = candidates.parallelStream()
                .map(candidate -> fetchOne(candidate, counter.incrementAndGet(), total))
                .filter(Objects::nonNull)
                .toList();

        log.info("Fetch summary: {}/{} succeeded", sources.size(), total);
        return sources;
    }

    private FetchedSource fetchOne(SearchCandidate candidate, int index, int total) {
        try {
            var page = pageFetcher.fetch(candidate.url());

            if (page.statusCode() == 200 && page.content() != null && page.content().length() > 200) {
                String content = page.content();
                if (content.length() > 15_000) {
                    content = content.substring(0, 15_000);
                }
                log.info("  [{}/{}] OK {} ({} chars)",
                        index, total, candidate.title(), page.content().length());
                return new FetchedSource(candidate.url(), candidate.title(), content, page.statusCode());
            } else {
                log.info("  [{}/{}] FAIL {} [{}]",
                        index, total, candidate.url(), page.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.info("  [{}/{}] FAIL {} — {}",
                    index, total, candidate.url(), e.getMessage());
            return null;
        }
    }
}