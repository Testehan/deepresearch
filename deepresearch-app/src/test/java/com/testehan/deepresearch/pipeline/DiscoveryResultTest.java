package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.model.SearchCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryResultTest {

    @Test
    void shouldCreateDiscoveryResult() {
        var candidates = List.of(
                new SearchCandidate("http://a.com", "Title A", "query1"),
                new SearchCandidate("http://b.com", "Title B", "query1")
        );
        
        var result = new DiscoveryService.DiscoveryResult(candidates, 2);
        
        assertEquals(2, result.candidates().size());
        assertEquals(2, result.queriesGenerated());
    }

    @Test
    void shouldReturnEmptyCandidates() {
        var result = new DiscoveryService.DiscoveryResult(List.of(), 0);
        
        assertTrue(result.candidates().isEmpty());
        assertEquals(0, result.queriesGenerated());
    }
}