package com.testehan.deepresearch.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryServiceTest {

    @Test
    void queryCountCapsAtEightForDeeperResearch() {
        assertEquals(8, DiscoveryService.queryCountFor(30));
    }

    @Test
    void browserbaseResultsPerQueryCapsAtTwentyFive() {
        assertEquals(25, DiscoveryService.resultsPerQueryFor(30));
    }

    @Test
    void browserbaseResultsPerQueryKeepsSmallerRequestsUnchanged() {
        assertEquals(18, DiscoveryService.resultsPerQueryFor(18));
        assertEquals(5, DiscoveryService.resultsPerQueryFor(5));
    }
}
