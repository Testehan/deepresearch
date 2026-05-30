package com.testehan.deepresearch.service;

import com.testehan.deepresearch.model.Diagnostics;
import com.testehan.deepresearch.model.LlmUsage;
import com.testehan.deepresearch.model.NewsReport;
import com.testehan.deepresearch.model.ResearchRequest;
import com.testehan.deepresearch.model.SourceReference;
import com.testehan.deepresearch.pipeline.BatchGeminiService;
import com.testehan.deepresearch.pipeline.DiscoveryService;
import com.testehan.deepresearch.pipeline.RetrievalService;
import com.testehan.deepresearch.pipeline.SynthesisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Service
class ResearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipeline.class);

    private final DiscoveryService discoveryService;
    private final RetrievalService retrievalService;
    private final SynthesisService synthesisService;
    private final BatchGeminiService batchGeminiService;

    ResearchPipeline(DiscoveryService discoveryService,
                     RetrievalService retrievalService,
                     SynthesisService synthesisService,
                     BatchGeminiService batchGeminiService) {
        this.discoveryService = discoveryService;
        this.retrievalService = retrievalService;
        this.synthesisService = synthesisService;
        this.batchGeminiService = batchGeminiService;
    }

    /** Regular (non-batch) execution — returns a complete report. */
    NewsReport execute(ResearchRequest request, LlmUsage usage) {
        long start = System.currentTimeMillis();
        log.info("Researching: \"{}\" with model: {}", request.subject(), request.resolvedModelId());

        var discoveryResult = discoveryService.discover(
                request.subject(),
                request.resolvedMaxSources(),
                request.resolvedDiscoveryPrompt(),
                usage,
                request.resolvedModelId()
        );
        var candidates = discoveryResult.candidates();
        var sources = retrievalService.retrieve(candidates);
        var report = synthesisService.synthesize(
                request.subject(),
                sources,
                request.resolvedChunkSize(),
                request.resolvedSynthesisPrompt(),
                request.resolvedCompileReportPrompt(),
                usage,
                request.resolvedModelId()
        );

        long duration = System.currentTimeMillis() - start;
        log.info("Done in {}s", duration / 1000);

        var sourceRefs = sources.stream()
                .map(s -> new SourceReference(s.url(), s.title()))
                .toList();

        var diagnostics = new Diagnostics(
                discoveryResult.queriesGenerated(),
                candidates.size(),
                sources.size(),
                sources.size(),
                duration);

        var newsReport = new NewsReport(
                request.topic(),
                report.executiveSummary(),
                report.keyFindings(),
                report.themes(),
                report.openQuestions(),
                sourceRefs,
                diagnostics);

        writeReport(newsReport);
        return newsReport;
    }

    /**
     * Batch execution: runs discovery + retrieval normally, then submits synthesis
     * chunk prompts to the Gemini Batch API.
     *
     * @return a record holding the Gemini batchJobId and the source refs for later report assembly
     */
    BatchSubmissionResult executeForBatch(ResearchRequest request, LlmUsage usage) {
        log.info("Researching (batch mode): \"{}\" with model: {}", request.subject(), request.resolvedModelId());

        var discoveryResult = discoveryService.discover(
                request.subject(),
                request.resolvedMaxSources(),
                request.resolvedDiscoveryPrompt(),
                usage,
                request.resolvedModelId()  // discovery uses one regular Gemini call, batch prefix stripped internally
        );
        var candidates = discoveryResult.candidates();
        var sources = retrievalService.retrieve(candidates);

        var sourceRefs = sources.stream()
                .map(s -> new SourceReference(s.url(), s.title()))
                .toList();

        List<String> chunkPrompts = synthesisService.buildChunkPrompts(
                request.subject(),
                sources,
                request.resolvedChunkSize(),
                request.resolvedSynthesisPrompt()
        );

        String batchJobId = batchGeminiService.submitBatch(request.resolvedModelId(), chunkPrompts);
        log.info("Batch job submitted: {}", batchJobId);

        return new BatchSubmissionResult(batchJobId, sourceRefs);
    }

    record BatchSubmissionResult(String batchJobId, List<SourceReference> sourceRefs) {}

    /** Assembles the final NewsReport once Gemini batch results are available. */
    NewsReport completeBatchResult(ResearchRequest request, List<String> chunkSummaries,
                                   List<SourceReference> sourceRefs, LlmUsage usage) {
        String allSummaries = String.join("\n\n---\n\n", chunkSummaries);
        var report = synthesisService.compileFinalReport(
                request.subject(),
                allSummaries,
                request.resolvedCompileReportPrompt(),
                usage,
                request.resolvedModelId()
        );

        var diagnostics = new Diagnostics(0, sourceRefs.size(), sourceRefs.size(), sourceRefs.size(), 0);
        var newsReport = new NewsReport(
                request.topic(),
                report.executiveSummary(),
                report.keyFindings(),
                report.themes(),
                report.openQuestions(),
                sourceRefs,
                diagnostics);

        writeReport(newsReport);
        return newsReport;
    }

    public void writeReport(NewsReport report) {
        try {
            var dir = Path.of("reports");
            Files.createDirectories(dir);

            String slug = report.topic().toString().replaceAll("[^a-z0-9]+", "-");
            String filename = "%s-%s.md".formatted(LocalDate.now(), slug);
            Path file = dir.resolve(filename);

            var md = new StringBuilder();
            md.append("# %s\n\n".formatted(report.topic()));
            md.append("## Executive Summary\n\n%s\n\n".formatted(report.executiveSummary()));

            md.append("## Key Findings\n\n");
            for (var finding : report.keyFindings()) {
                md.append("- %s\n".formatted(finding));
            }

            md.append("\n## Themes\n\n");
            for (var theme : report.themes()) {
                md.append("- %s\n".formatted(theme));
            }

            md.append("\n## Open Questions\n\n");
            for (var question : report.openQuestions()) {
                md.append("- %s\n".formatted(question));
            }

            md.append("\n## Sources\n\n");
            for (var source : report.sources()) {
                md.append("- [%s](%s)\n".formatted(source.title(), source.url()));
            }

            md.append("\n---\n*Generated in %ds | %d sources from %d URLs*\n".formatted(
                    report.diagnostics().durationMs() / 1000,
                    report.diagnostics().sourcesUsed(),
                    report.diagnostics().urlsDiscovered()));

            log.info(md.toString());

            Files.writeString(file, md.toString());
            log.info("Report saved to {}", file);
        } catch (IOException e) {
            log.warn("Failed to save report: {}", e.getMessage());
        }
    }
}
