package com.testehan.deepresearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

@Service
class ResearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipeline.class);

    private final DiscoveryService discoveryService;
    private final RetrievalService retrievalService;
    private final SynthesisService synthesisService;

    ResearchPipeline(DiscoveryService discoveryService,
                     RetrievalService retrievalService,
                     SynthesisService synthesisService) {
        this.discoveryService = discoveryService;
        this.retrievalService = retrievalService;
        this.synthesisService = synthesisService;
    }

    ResearchReport execute(String topic) {
        long start = System.currentTimeMillis();
        log.info("Researching: \"{}\"", topic);

        var discoveryResult = discoveryService.discover(topic);
        var candidates = discoveryResult.candidates();
        var sources = retrievalService.retrieve(candidates);
        var report = synthesisService.synthesize(topic, sources);

        long duration = System.currentTimeMillis() - start;
        log.info("Done in {}s", duration / 1000);

        var sourceRefs = sources.stream()
                .map(s -> new ResearchReport.SourceReference(s.url(), s.title()))
                .toList();

        var diagnostics = new ResearchReport.Diagnostics(
                discoveryResult.queriesGenerated(), candidates.size(), sources.size(), sources.size(), duration);

        var researchReport = new ResearchReport(
                topic,
                report.executiveSummary(),
                report.keyFindings(),
                report.themes(),
                report.openQuestions(),
                sourceRefs,
                diagnostics
        );

        writeReport(researchReport);
        return researchReport;
    }

    private void writeReport(ResearchReport report) {
        try {
            var dir = Path.of("reports");
            Files.createDirectories(dir);

            String slug = report.topic().toLowerCase().replaceAll("[^a-z0-9]+", "-");
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
