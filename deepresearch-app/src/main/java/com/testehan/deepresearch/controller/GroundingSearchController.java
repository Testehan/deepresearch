package com.testehan.deepresearch.controller;

import com.testehan.deepresearch.model.GroundingSearchRequest;
import com.testehan.deepresearch.model.GroundingSearchResponse;
import com.testehan.deepresearch.model.LlmUsage;
import com.testehan.deepresearch.pipeline.DiscoveryService;
import com.testehan.deepresearch.pipeline.RetrievalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroundingSearchController {

    private final DiscoveryService discoveryService;
    private final RetrievalService retrievalService;

    public GroundingSearchController(DiscoveryService discoveryService, RetrievalService retrievalService) {
        this.discoveryService = discoveryService;
        this.retrievalService = retrievalService;
    }

    @PostMapping("/api/grounding/search")
    public ResponseEntity<GroundingSearchResponse> search(@RequestBody GroundingSearchRequest request) {
        if (request.subject() == null || request.subject().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        LlmUsage usage = new LlmUsage();
        var discovery = discoveryService.discover(
                request.subject(),
                request.resolvedMaxSources(),
                request.resolvedDiscoveryPrompt(),
                usage,
                request.resolvedModelId());
        var sources = retrievalService.retrieve(discovery.candidates());

        return ResponseEntity.ok(new GroundingSearchResponse(
                discovery.queries(),
                discovery.candidates(),
                sources,
                usage));
    }
}
