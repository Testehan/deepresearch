package com.testehan.deepresearch.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResearchRequestTest {

    @Test
    void defaultMaxSources_shouldReturn15() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, null, null);
        assertEquals(15, request.resolvedMaxSources());
    }

    @Test
    void defaultChunkSize_shouldReturn4() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, null, null);
        assertEquals(4, request.resolvedChunkSize());
    }

    @Test
    void customMaxSources_shouldReturnCustomValue() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, 5, null, null, null, null);
        assertEquals(5, request.resolvedMaxSources());
    }

    @Test
    void customChunkSize_shouldReturnCustomValue() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, 2, null, null, null);
        assertEquals(2, request.resolvedChunkSize());
    }

    @Test
    void defaultDiscoveryPrompt_shouldNotBeNull() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, null, null);
        assertNotNull(request.resolvedDiscoveryPrompt());
        assertTrue(request.resolvedDiscoveryPrompt().contains("%d"));
    }

    @Test
    void customDiscoveryPrompt_shouldReturnCustom() {
        var customPrompt = "Custom prompt for %s";
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, customPrompt, null, null);
        assertEquals(customPrompt, request.resolvedDiscoveryPrompt());
    }

    @Test
    void defaultSynthesisPrompt_shouldNotBeNull() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, null, null);
        assertNotNull(request.resolvedSynthesisPrompt());
    }

    @Test
    void customSynthesisPrompt_shouldReturnCustom() {
        var customPrompt = "Custom analyze prompt";
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, customPrompt, null);
        assertEquals(customPrompt, request.resolvedSynthesisPrompt());
    }

    @Test
    void defaultCompileReportPrompt_shouldNotBeNull() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, null, null);
        assertNotNull(request.resolvedCompileReportPrompt());
    }

    @Test
    void customCompileReportPrompt_shouldReturnCustom() {
        var customPrompt = "Custom compile prompt";
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, null, customPrompt);
        assertEquals(customPrompt, request.resolvedCompileReportPrompt());
    }

    @Test
    void zeroMaxSources_shouldFallbackToDefault() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, 0, null, null, null, null);
        assertEquals(15, request.resolvedMaxSources());
    }

    @Test
    void negativeMaxSources_shouldFallbackToDefault() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, -5, null, null, null, null);
        assertEquals(15, request.resolvedMaxSources());
    }

    @Test
    void modelId_ollama_isNotBatch() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", "ollama", null, null, null, null, null);
        assertFalse(request.isBatchModel());
        assertEquals("ollama", request.resolvedModelId());
    }

    @Test
    void modelId_batchGemini_isBatch() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", "batch-gemini-2.5-pro", null, null, null, null, null);
        assertTrue(request.isBatchModel());
    }

    @Test
    void modelId_null_defaultsToOllama() {
        var request = new ResearchRequest(ResearchTopic.NEWS, "test subject", null, null, null, null, null, null);
        assertEquals("ollama", request.resolvedModelId());
        assertFalse(request.isBatchModel());
    }
}
