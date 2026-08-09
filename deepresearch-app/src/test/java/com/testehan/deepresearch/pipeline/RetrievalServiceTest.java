package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.model.SearchCandidate;
import dev.danvega.browserbase.model.exception.BrowserbaseException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class RetrievalServiceTest {

    @Test
    void retrieve_LogsBrowserbaseStatusAndBody(CapturedOutput output) {
        PageFetcher pageFetcher = mock(PageFetcher.class);
        RetrievalService retrievalService = new RetrievalService(pageFetcher);
        SearchCandidate candidate = new SearchCandidate(
                "https://example.com/article",
                "Example article",
                "Example query"
        );

        when(pageFetcher.fetch(candidate.url()))
                .thenThrow(new BrowserbaseException("Unexpected status: 402", null, 402, "{}"));

        assertThat(retrievalService.retrieve(List.of(candidate))).isEmpty();
        assertThat(output.getOut())
                .contains("Browserbase BrowserbaseException status=402 body={}")
                .contains("title=\"Example article\"")
                .contains("query=\"Example query\"");
    }

    @Test
    void retrieve_LogsEmptyBrowserbaseBody(CapturedOutput output) {
        PageFetcher pageFetcher = mock(PageFetcher.class);
        RetrievalService retrievalService = new RetrievalService(pageFetcher);
        SearchCandidate candidate = new SearchCandidate(
                "https://example.com/article",
                "Example article",
                "Example query"
        );

        when(pageFetcher.fetch(candidate.url()))
                .thenThrow(new BrowserbaseException("Unexpected status: 402", null, 402, ""));

        assertThat(retrievalService.retrieve(List.of(candidate))).isEmpty();
        assertThat(output.getOut()).contains("status=402 body=<empty>");
    }
}
