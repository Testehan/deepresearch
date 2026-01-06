package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.Diagnostics;
import com.testehan.deepresearch.model.FetchedSource;
import com.testehan.deepresearch.model.ResearchReport;
import com.testehan.deepresearch.model.ResearchRequest;
import com.testehan.deepresearch.model.SourceReference;
import com.testehan.deepresearch.pipeline.DiscoveryService;
import com.testehan.deepresearch.pipeline.RetrievalService;
import com.testehan.deepresearch.pipeline.SynthesisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResearchPipelineTest {

    @Mock
    private DiscoveryService discoveryService;

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private SynthesisService synthesisService;

    private ResearchPipeline pipeline;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pipeline = new ResearchPipeline(discoveryService, retrievalService, synthesisService);
    }

    @Test
    void execute_shouldCallAllServicesInOrder() {
        var request = new ResearchRequest("test topic", 5, 2, "prompt", "prompt");
        
        var discoveryResult = new DiscoveryService.DiscoveryResult(
                List.of(new com.testehan.deepresearch.model.SearchCandidate("http://a.com", "A", "q")), 1);
        when(discoveryService.discover(eq("test topic"), eq(5), anyString())).thenReturn(discoveryResult);
        
        var sources = List.of(new FetchedSource("http://a.com", "A", "content", 200));
        when(retrievalService.retrieve(any())).thenReturn(sources);
        
        var report = new SynthesisService.Report("summary", List.of("finding"), List.of("theme"), List.of("q"));
        when(synthesisService.synthesize(eq("test topic"), eq(sources), eq(2), anyString(), anyString()))
                .thenReturn(report);

        var result = pipeline.execute(request);

        assertNotNull(result);
        assertEquals("test topic", result.topic());
        verify(discoveryService).discover(eq("test topic"), eq(5), anyString());
        verify(retrievalService).retrieve(any());
        verify(synthesisService).synthesize(eq("test topic"), any(), eq(2), anyString(), anyString());
    }

    @Test
    void execute_shouldCreateReportWithCorrectDiagnostics() {
        var request = new ResearchRequest("topic", 3, 2, "p", "p");
        
        var discoveryResult = new DiscoveryService.DiscoveryResult(
                List.of(
                        new com.testehan.deepresearch.model.SearchCandidate("http://1.com", "1", "q"),
                        new com.testehan.deepresearch.model.SearchCandidate("http://2.com", "2", "q")
                ), 1);
        when(discoveryService.discover(anyString(), anyInt(), anyString())).thenReturn(discoveryResult);
        
        var sources = List.of(new FetchedSource("http://1.com", "1", "content", 200));
        when(retrievalService.retrieve(any())).thenReturn(sources);
        
        var report = new SynthesisService.Report("summary", List.of("f"), List.of(), List.of());
        when(synthesisService.synthesize(anyString(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(report);

        var result = pipeline.execute(request);

        var diag = result.diagnostics();
        assertEquals(1, diag.queriesGenerated());
        assertEquals(2, diag.urlsDiscovered());
        assertEquals(1, diag.urlsFetched());
        assertTrue(diag.durationMs() >= 0);
    }

    @Test
    void execute_shouldMapSourcesToReferences() {
        var request = new ResearchRequest("topic", 1, 1, "p", "p");
        
        var discoveryResult = new DiscoveryService.DiscoveryResult(List.of(), 1);
        when(discoveryService.discover(anyString(), anyInt(), anyString())).thenReturn(discoveryResult);
        
        var sources = List.of(
                new FetchedSource("http://a.com", "Title A", "content", 200),
                new FetchedSource("http://b.com", "Title B", "content", 200)
        );
        when(retrievalService.retrieve(any())).thenReturn(sources);
        
        var report = new SynthesisService.Report("s", List.of(), List.of(), List.of());
        when(synthesisService.synthesize(anyString(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(report);

        var result = pipeline.execute(request);

        assertEquals(2, result.sources().size());
        assertTrue(result.sources().stream().anyMatch(s -> s.url().equals("http://a.com")));
        assertTrue(result.sources().stream().anyMatch(s -> s.url().equals("http://b.com")));
    }
}