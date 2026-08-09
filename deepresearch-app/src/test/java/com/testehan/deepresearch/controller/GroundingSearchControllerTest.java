package com.testehan.deepresearch.controller;

import com.testehan.deepresearch.model.FetchedSource;
import com.testehan.deepresearch.model.GroundingSearchRequest;
import com.testehan.deepresearch.model.SearchCandidate;
import com.testehan.deepresearch.pipeline.DiscoveryService;
import com.testehan.deepresearch.pipeline.RetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroundingSearchControllerTest {

    private final DiscoveryService discoveryService = mock(DiscoveryService.class);
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final GroundingSearchController controller = new GroundingSearchController(discoveryService, retrievalService);

    @Test
    void searchReturnsFetchedSources() {
        var candidate = new SearchCandidate("https://example.com", "Example", "query");
        var source = new FetchedSource("https://example.com", "Example", "content", 200);
        when(discoveryService.discover(eq("AAPL accounting"), eq(3), any(), any(), eq("gemini-3.1-flash-lite")))
                .thenReturn(new DiscoveryService.DiscoveryResult(List.of("query"), List.of(candidate), 1));
        when(retrievalService.retrieve(List.of(candidate))).thenReturn(List.of(source));

        var response = controller.search(new GroundingSearchRequest(
                "AAPL accounting", "gemini-3.1-flash-lite", 3, null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of("query"), response.getBody().queries());
        assertEquals(List.of(source), response.getBody().sources());
        verify(retrievalService).retrieve(List.of(candidate));
    }

    @Test
    void searchRejectsBlankSubject() {
        var response = controller.search(new GroundingSearchRequest(" ", "gemini-3.1-flash-lite", 3, null));

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody() == null);
    }
}
